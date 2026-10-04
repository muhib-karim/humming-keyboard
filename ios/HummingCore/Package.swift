// swift-tools-version:5.7
// Humming's platform-independent Swift code (dictation clean-up). The iOS app and keyboard compile the same
// sources; this package lets the clean-up tests run with `swift test` on Linux and macOS too.
import PackageDescription

let package = Package(
    name: "HummingCore",
    products: [.library(name: "HummingCore", targets: ["HummingCore"])],
    targets: [
        .target(name: "HummingCore"),
        .testTarget(name: "HummingCoreTests", dependencies: ["HummingCore"]),
    ]
)
