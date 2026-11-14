//! Decision mathematics for sampled boss-battle results.
//!
//! This module does not simulate a CIFI fight. It quantifies uncertainty in a
//! simulator's independent win/loss trials and estimates repeated-attempt odds.
//! It must not be presented as evidence that the underlying simulator matches
//! CIFI, nor used with correlated / adaptively selected samples as if iid.

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Estimate {
    pub wins: u64,
    pub trials: u64,
    pub probability: f64,
    pub lower_95: f64,
    pub upper_95: f64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum InputError { NoTrials, WinsExceedTrials, InvalidProbability, TooManyTrials }

impl Estimate {
    /// Wilson score interval, two-sided 95% normal quantile. No wins and all
    /// wins remain uncertain; this avoids a misleading 0% or 100% guarantee.
    pub fn from_counts(wins: u64, trials: u64) -> Result<Self, InputError> {
        if trials == 0 { return Err(InputError::NoTrials); }
        if wins > trials { return Err(InputError::WinsExceedTrials); }
        // Keep count-to-f64 conversion exact; callers with larger counts can
        // supply a compact, independently validated aggregation instead.
        if trials > (1_u64 << 53) { return Err(InputError::TooManyTrials); }
        let n = trials as f64;
        let p = wins as f64 / n;
        let z: f64 = 1.959_963_984_540_054;
        let z2 = z * z;
        let denom = 1.0 + z2 / n;
        let center = (p + z2 / (2.0 * n)) / denom;
        let half = z * (p * (1.0 - p) / n + z2 / (4.0 * n * n)).sqrt() / denom;
        Ok(Self { wins, trials, probability: p,
            lower_95: (center - half).clamp(0.0, 1.0),
            upper_95: (center + half).clamp(0.0, 1.0) })
    }

    /// Probability of at least one victory in `attempts` independent attempts
    /// with constant per-attempt probability. The bounds propagate the Wilson
    /// endpoints; they are not a guarantee about actual game behavior.
    pub fn chance_within(self, attempts: u64) -> Result<(f64, f64, f64), InputError> {
        Ok((chance(self.lower_95, attempts)?, chance(self.probability, attempts)?,
            chance(self.upper_95, attempts)?))
    }
}

pub fn chance(p: f64, attempts: u64) -> Result<f64, InputError> {
    if !p.is_finite() || !(0.0..=1.0).contains(&p) { return Err(InputError::InvalidProbability); }
    if attempts > (1_u64 << 53) { return Err(InputError::TooManyTrials); }
    if attempts == 0 || p == 0.0 { return Ok(0.0); }
    if p == 1.0 { return Ok(1.0); }
    Ok(-((attempts as f64) * (-p).ln_1p()).exp_m1())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn close(a: f64, b: f64, tol: f64) { assert!((a-b).abs() < tol, "{a} != {b}"); }
    #[test] fn wilson_known_reference() {
        let x=Estimate::from_counts(50,100).unwrap();
        close(x.lower_95,0.4038315304,1e-9);
        close(x.upper_95,0.5961684696,1e-9);
    }
    #[test] fn extremes_are_not_certainties() {
        let zero=Estimate::from_counts(0,10).unwrap();
        let all=Estimate::from_counts(10,10).unwrap();
        assert_eq!(zero.lower_95,0.0); assert!(zero.upper_95>0.27);
        assert!(all.lower_95<0.73); close(all.upper_95,1.0,1e-15);
    }
    #[test] fn repeated_attempts() {
        close(chance(0.2,3).unwrap(),0.488,1e-14);
        close(chance(1e-12,1_000_000).unwrap(),0.00000099999950000067,1e-16);
        assert_eq!(chance(1.0,0),Ok(0.0)); assert_eq!(chance(1.0,1),Ok(1.0));
    }
    #[test] fn rejects_invalid_counts_and_probabilities() {
        assert_eq!(Estimate::from_counts(0,0),Err(InputError::NoTrials));
        assert_eq!(Estimate::from_counts(2,1),Err(InputError::WinsExceedTrials));
        assert_eq!(Estimate::from_counts(0,(1_u64<<53)+1),Err(InputError::TooManyTrials));
        assert_eq!(chance(f64::NAN,2),Err(InputError::InvalidProbability));
        assert_eq!(chance(-0.1,2),Err(InputError::InvalidProbability));
    }
    #[test] fn interval_propagation_is_ordered() {
        let (lo,mid,hi)=Estimate::from_counts(13,100).unwrap().chance_within(5).unwrap();
        assert!(0.0 < lo && lo < mid && mid < hi && hi < 1.0);
    }
}
