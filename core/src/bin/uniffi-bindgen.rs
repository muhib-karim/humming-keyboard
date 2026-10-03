//! Generates Kotlin/Swift bindings from a built library:
//!   cargo run --features bindgen-cli --bin uniffi-bindgen -- generate --library <lib> --language kotlin --out-dir out
fn main() {
    uniffi::uniffi_bindgen_main()
}
