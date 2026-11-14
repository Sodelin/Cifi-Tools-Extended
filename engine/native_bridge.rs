//! Stable primitive JNI surface for the native app's boss decision math.
//! Invalid inputs return NaN; callers must validate before displaying results.
mod boss_math;
use std::ffi::c_void;

#[no_mangle]
pub extern "system" fn Java_com_cifi_assistant_NativeBossMath_estimate(
    _env: *mut c_void, _class: *mut c_void, wins: i64, trials: i64, field: i32,
) -> f64 {
    if wins < 0 || trials < 0 { return f64::NAN; }
    match boss_math::Estimate::from_counts(wins as u64, trials as u64) {
        Ok(e) => match field { 0 => e.probability, 1 => e.lower_95, 2 => e.upper_95, _ => f64::NAN },
        Err(_) => f64::NAN,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cifi_assistant_NativeBossMath_chance(
    _env: *mut c_void, _class: *mut c_void, probability: f64, attempts: i64,
) -> f64 {
    if attempts < 0 { return f64::NAN; }
    boss_math::chance(probability, attempts as u64).unwrap_or(f64::NAN)
}

#[no_mangle]
pub extern "system" fn Java_com_cifi_assistant_NativeBossMath_chanceWithin(
    _env: *mut c_void, _class: *mut c_void, wins: i64, trials: i64, attempts: i64, field: i32,
) -> f64 {
    if wins < 0 || trials < 0 || attempts < 0 { return f64::NAN; }
    match boss_math::Estimate::from_counts(wins as u64, trials as u64)
        .and_then(|e| e.chance_within(attempts as u64)) {
        Ok((lower, point, upper)) => match field { 0 => point, 1 => lower, 2 => upper, _ => f64::NAN },
        Err(_) => f64::NAN,
    }
}
