rootProject.name = "OneVoid"

// Test-only stand-in for Waystation 0.4.0's hand-over API, until that release exists. Never shipped.
include("waystation-standin")
project(":waystation-standin").projectDir = file("test/standin")
