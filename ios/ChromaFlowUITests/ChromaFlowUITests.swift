import XCTest

/// Walks through the whole app on a simulator: styles, colors, play mode with finger strokes,
/// symmetry, auto-stir, Fine-tune, Settings, links, rotation, background/foreground and
/// settings surviving a relaunch. Screenshots are attached to the test result.
final class ChromaFlowUITests: XCTestCase {

    private let app = XCUIApplication()
    private var shotIndex = 0

    override func setUp() {
        continueAfterFailure = true
    }

    // MARK: helpers

    private func shot(_ name: String) {
        shotIndex += 1
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = String(format: "%02d-%@", shotIndex, name)
        a.lifetime = .keepAlways
        add(a)
    }

    private func find(_ prefix: String) -> XCUIElement {
        app.webViews.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
    }

    private func pause(_ s: TimeInterval) { Thread.sleep(forTimeInterval: s) }

    /// Scrolls the menu until the element sits clear of the pinned Play bar, then taps it.
    @discardableResult
    private func tap(_ prefix: String, file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let el = find(prefix)
        guard el.waitForExistence(timeout: 6) else {
            XCTFail("Couldn't find “\(prefix)”", file: file, line: line); return el
        }
        reveal(el)
        el.tap()
        pause(0.6)
        return el
    }

    private func reveal(_ el: XCUIElement) {
        let h = app.windows.firstMatch.frame.height
        for _ in 0..<14 {
            let f = el.frame
            if f.minY > 40 && f.maxY < h - 130 { return }
            let web = app.webViews.firstMatch
            let from = web.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.62))
            let to = web.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: f.midY > h / 2 ? 0.30 : 0.94))
            from.press(forDuration: 0.05, thenDragTo: to)
            pause(0.4)
        }
    }

    private func stroke(_ a: CGVector, _ b: CGVector) {
        let w = app.windows.firstMatch
        w.coordinate(withNormalizedOffset: a).press(forDuration: 0.05, thenDragTo: w.coordinate(withNormalizedOffset: b))
    }

    private func paint() {
        stroke(CGVector(dx: 0.2, dy: 0.25), CGVector(dx: 0.8, dy: 0.45))
        stroke(CGVector(dx: 0.75, dy: 0.7), CGVector(dx: 0.25, dy: 0.55))
        stroke(CGVector(dx: 0.5, dy: 0.15), CGVector(dx: 0.45, dy: 0.85))
        pause(0.3)
    }

    /// Swipe in from the left edge, the iPhone version of Android's Back.
    private func edgeBack() {
        let w = app.windows.firstMatch
        w.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5))
            .press(forDuration: 0.05, thenDragTo: w.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: 0.5)))
        pause(1.0)
    }

    private var playButton: XCUIElement { find("Play ") }

    // MARK: the walk-through

    func testEverything() {
        XCUIDevice.shared.orientation = .portrait
        app.launch()
        XCTAssertTrue(playButton.waitForExistence(timeout: 15), "The menu didn't load")
        pause(2.5)
        shot("menu")

        // Styles
        tap("Ocean")
        XCTAssertTrue(playButton.label.contains("Ocean"), "Play button should say Ocean, says \(playButton.label)")
        pause(1.5); shot("style-ocean")

        // Play mode, finger strokes, back
        playButton.tap(); pause(1.5)
        XCTAssertFalse(playButton.isHittable, "The menu should be hidden while playing")
        paint(); shot("play-ocean-strokes")
        edgeBack()
        XCTAssertTrue(playButton.isHittable, "Edge swipe should return to the menu")
        shot("back-to-menu")

        // Colors
        tap("Fire"); tap("Rainbow"); pause(1); shot("colors-rainbow")
        tap("My colors"); pause(1); shot("colors-mine")
        tap("My color 1"); pause(1.5); shot("color-picker")
        app.swipeDown(velocity: .fast); pause(1)
        if app.buttons["Close"].exists { app.buttons["Close"].tap(); pause(1) }
        tap("Style"); pause(0.5)

        // Symmetry + play
        tap("Neon"); tap("Kaleido")
        playButton.tap(); pause(1); paint(); shot("play-kaleido")
        edgeBack()

        // Auto-stir: the fluid moves with no touch at all
        tap("Off"); tap("Auto-stir")
        playButton.tap(); pause(4); shot("play-autostir-no-touch")
        edgeBack()
        tap("Auto-stir")

        // Fine-tune: open, drag the Swirl and Glow sliders, reset
        tap("Fine-tune")
        let sliders = app.webViews.sliders
        XCTAssertTrue(sliders.firstMatch.waitForExistence(timeout: 5), "No sliders in Fine-tune")
        if sliders.count > 3 {
            for i in [3, 5] {
                let s = sliders.element(boundBy: i)
                reveal(s)
                s.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.5))
                    .press(forDuration: 0.05, thenDragTo: s.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)))
                pause(0.5)
            }
        }
        shot("fine-tune-changed")
        tap("Reset Neon"); pause(0.5); shot("fine-tune-reset")

        // Settings screen
        let gear = app.webViews.buttons["Settings"]
        reveal(gear)
        gear.tap(); pause(1.2); shot("settings")
        XCTAssertFalse(find("Set up live wallpaper").exists, "The live wallpaper section should be hidden on iPhone")
        XCTAssertTrue(find("Credits to Chinni").exists, "Credits missing in Settings")
        tap("Low"); pause(1); shot("settings-quality-low")
        tap("High")
        tap("Reset everything"); tap("Tap again"); pause(1); shot("settings-reset")

        // Links open in Safari
        tap("Website")
        pause(3)
        XCTAssertNotEqual(app.state, .runningForeground, "The Website link should open Safari")
        shot("website-in-safari")
        app.activate(); pause(2)
        tap("Back"); pause(1); shot("settings-closed")

        // Rotation
        XCUIDevice.shared.orientation = .landscapeLeft; pause(2); shot("landscape-menu")
        playButton.tap(); pause(1); paint(); shot("landscape-play")
        edgeBack()
        XCUIDevice.shared.orientation = .portrait; pause(2)

        // Background and back: the fluid must still draw
        tap("Galaxy"); pause(1)
        XCUIDevice.shared.press(.home); pause(2)
        app.activate(); pause(2.5); shot("resumed-galaxy")

        // Settings survive a relaunch
        app.terminate()
        app.launch()
        XCTAssertTrue(playButton.waitForExistence(timeout: 15))
        pause(2.5)
        XCTAssertTrue(playButton.label.contains("Galaxy"), "After relaunch the style should still be Galaxy, is \(playButton.label)")
        shot("relaunch-kept-galaxy")
    }
}
