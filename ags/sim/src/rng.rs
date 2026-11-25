//! The game's only source of chance: SplitMix64, saved with the game so a seed replays exactly.
//! Determinism rule: the simulation uses only + − × ÷ and sqrt, never transcendental functions,
//! so native and WebAssembly builds give bit-identical results.

#[derive(Clone, Debug)]
pub struct Rng(pub u64);

impl Rng {
    pub fn new(seed: u64) -> Self { Rng(seed ^ 0x5DEE_CE66_D1A4_F00D) }
    pub fn next_u64(&mut self) -> u64 {
        self.0 = self.0.wrapping_add(0x9E37_79B9_7F4A_7C15);
        let mut z = self.0;
        z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
        z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
        z ^ (z >> 31)
    }
    /// Uniform in [0, 1).
    pub fn f64(&mut self) -> f64 { (self.next_u64() >> 11) as f64 / (1u64 << 53) as f64 }
    /// Approximately normal (mean 0, sd 1): the Irwin–Hall sum of six uniforms, no logarithms.
    pub fn gauss(&mut self) -> f64 { let mut s = 0.0; for _ in 0..6 { s += self.f64(); } (s - 3.0) * std::f64::consts::SQRT_2 }
    pub fn below(&mut self, n: usize) -> usize { if n == 0 { 0 } else { (self.next_u64() % n as u64) as usize } }
    pub fn chance(&mut self, p: f64) -> bool { self.f64() < p }
    pub fn d6(&mut self) -> u32 { 1 + (self.next_u64() % 6) as u32 }
    pub fn pick<'a, T>(&mut self, v: &'a [T]) -> &'a T { &v[self.below(v.len())] }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn same_seed_same_stream() {
        let (mut a, mut b) = (Rng::new(42), Rng::new(42));
        for _ in 0..1000 { assert_eq!(a.next_u64(), b.next_u64()); }
    }
    #[test]
    fn gauss_is_centred() {
        let mut r = Rng::new(7); let n = 20000; let mut s = 0.0; let mut s2 = 0.0;
        for _ in 0..n { let g = r.gauss(); s += g; s2 += g * g; }
        let mean = s / n as f64; let var = s2 / n as f64 - mean * mean;
        assert!(mean.abs() < 0.03, "mean {mean}"); assert!((var - 1.0).abs() < 0.05, "var {var}");
    }
}
