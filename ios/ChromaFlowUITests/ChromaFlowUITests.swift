import XCTest

/// Runs the in-app autopilot (ChromaFlow/Debug/autopilot.js, Debug builds only): it taps through
/// every style, color source, play option, touch shape, Fine-tune, the resets and Settings,
/// draws strokes, and checks 42 things along the way. XCUITest can't see inside the web view,
/// so the autopilot does the checking; if every check passes it opens the Website link,
/// which sends the app to the background.
final class ChromaFlowUITests: XCTestCase {

    func testEverything() {
        let app = XCUIApplication()
        app.launchArguments = ["-autopilot"]
        app.launch()
        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        let leftForSafari = safari.wait(for: .runningForeground, timeout: 420)

        let shot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        shot.name = "end"; shot.lifetime = .keepAlways
        add(shot)
        XCTAssertTrue(leftForSafari, "The autopilot reported a failed check (see the device log: AUTOPILOT FAIL)")
    }
}
