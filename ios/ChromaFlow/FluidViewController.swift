import SwiftUI
import UIKit
import WebKit

/// Hosts the fluid engine full screen. The page was written for Android and talks to a
/// `ChromaAndroid` object; here that object forwards to native code. The live wallpaper
/// is Android only (iOS has no live wallpapers), so the page hides that section.
final class FluidViewController: UIViewController, WKNavigationDelegate, WKScriptMessageHandler {

    private var web: WKWebView!
    /// The native Liquid Glass menu and Settings, shown over the fluid while not playing.
    private let menu = MenuBridge()
    private var menuHost: UIHostingController<NativeMenuRoot>!
    private static let void = UIColor(red: 7 / 255, green: 8 / 255, blue: 12 / 255, alpha: 1)

    override func loadView() {
        let content = WKUserContentController()
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        content.addUserScript(WKUserScript(source: """
            window.ChromaIOS = true;
            window.ChromaNativeMenu = true;
            window.ChromaVersion = "\(version)";
            window.ChromaAndroid = {
              setPlaying: function (on) { window.webkit.messageHandlers.chroma.postMessage({ playing: !!on }); }
            };
            """, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        // Follow the phone's text size (Settings > Display & Brightness > Text Size), like Android.
        let textScale = Int((UIFontMetrics(forTextStyle: .body).scaledValue(for: 100)).rounded())
        content.addUserScript(WKUserScript(source: "document.documentElement.style.webkitTextSizeAdjust = '\(textScale)%';",
                                           injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        content.add(WeakMessageHandler(self), name: "chroma")
        #if DEBUG
        // Demo and self-test: launch with -autopilot (see Debug/autopilot.js). Debug builds only.
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-storeshots") {
            content.addUserScript(WKUserScript(source: "window.AP_STORE = true;", injectionTime: .atDocumentStart, forMainFrameOnly: true))
        }
        if args.contains("-autopilot") || args.contains("-storeshots"),
           let url = Bundle.main.url(forResource: "autopilot", withExtension: "js"),
           let js = try? String(contentsOf: url, encoding: .utf8) {
            content.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
            content.add(AutopilotLog(), name: "autopilot")
        }
        #endif

        let config = WKWebViewConfiguration()
        config.userContentController = content
        web = WKWebView(frame: .zero, configuration: config)
        web.isOpaque = false
        web.backgroundColor = Self.void
        web.scrollView.backgroundColor = Self.void
        web.scrollView.isScrollEnabled = false
        web.scrollView.bounces = false
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.scrollView.pinchGestureRecognizer?.isEnabled = false
        web.allowsLinkPreview = false
        web.navigationDelegate = self
        #if DEBUG
        if #available(iOS 16.4, *) { web.isInspectable = true }
        #endif
        let root = UIView()
        root.backgroundColor = Self.void
        web.translatesAutoresizingMaskIntoConstraints = false
        root.addSubview(web)
        NSLayoutConstraint.activate([
            web.leadingAnchor.constraint(equalTo: root.leadingAnchor), web.trailingAnchor.constraint(equalTo: root.trailingAnchor),
            web.topAnchor.constraint(equalTo: root.topAnchor), web.bottomAnchor.constraint(equalTo: root.bottomAnchor),
        ])
        view = root
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        let dir = Bundle.main.resourceURL!.appendingPathComponent("web", isDirectory: true)
        web.loadFileURL(dir.appendingPathComponent("index.html"), allowingReadAccessTo: dir)
        CTFontManagerRegisterFontsForURL(dir.appendingPathComponent("fonts/Unbounded.ttf") as CFURL, .process, nil)

        menu.web = web
        menuHost = UIHostingController(rootView: NativeMenuRoot(bridge: menu))
        menuHost.view.backgroundColor = .clear
        addChild(menuHost)
        menuHost.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(menuHost.view)
        NSLayoutConstraint.activate([
            menuHost.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            menuHost.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            menuHost.view.topAnchor.constraint(equalTo: view.topAnchor),
            menuHost.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        menuHost.didMove(toParent: self)

        // Swiping in from the left edge works like Android's Back: it leaves play mode,
        // closes Settings or the setup screens. On the menu itself it does nothing.
        let edge = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(edgeSwipe(_:)))
        edge.edges = .left
        view.addGestureRecognizer(edge)
    }

    @objc private func edgeSwipe(_ g: UIScreenEdgePanGestureRecognizer) {
        guard g.state == .ended, g.translation(in: view).x > 60 else { return }
        if menu.showSettings { menu.showSettings = false; return }
        web.evaluateJavaScript("window.chromaBack && window.chromaBack()", completionHandler: nil)
    }

    // Keep the screen awake while playing, like the Android app.
    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any], let playing = body["playing"] as? Bool else { return }
        UIApplication.shared.isIdleTimerDisabled = playing
        showMenu(!playing)
    }

    /// While playing only the fluid is on screen; coming back shows the native menu again.
    private func showMenu(_ on: Bool) {
        if on { menu.refresh(); menuHost.view.isHidden = false }
        UIView.animate(withDuration: 0.3, animations: { self.menuHost.view.alpha = on ? 1 : 0 },
                       completion: { _ in if !on { self.menuHost.view.isHidden = true } })
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        menu.refresh()
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-nativeSettings") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { self.menu.showSettings = true }
        }
        // Self-check of the native menu: pick Ocean, Rainbow colors, Play, then Back.
        if ProcessInfo.processInfo.arguments.contains("-nativeDemo") {
            let steps: [(Double, () -> Void)] = [
                (3, { self.menu.act("click", "style-ocean") }),
                (5, { self.menu.act("click", "src-rainbow") }),
                (8, { self.menu.act("click", "play") }),
                (13, { self.web.evaluateJavaScript("window.chromaBack && window.chromaBack()", completionHandler: nil) }),
            ]
            for (t, step) in steps { DispatchQueue.main.asyncAfter(deadline: .now() + t, execute: step) }
        }
        #endif
    }

    // The page never navigates away from the bundled file; web links open in Safari.
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = action.request.url else { return decisionHandler(.cancel) }
        if url.isFileURL { return decisionHandler(.allow) }
        if url.scheme == "http" || url.scheme == "https" { UIApplication.shared.open(url) }
        decisionHandler(.cancel)
    }

    // iOS can stop the page's process in the background; settings are saved, so just reload.
    // If it keeps crashing (the graphics can't start, as for this iPhone app on a Mac), stop
    // reloading and open the page in no-graphics mode, which explains instead of looping.
    private var crashes: [Date] = []
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        UIApplication.shared.isIdleTimerDisabled = false
        crashes = crashes.filter { $0.timeIntervalSinceNow > -60 } + [Date()]
        if crashes.count >= 2 {
            let dir = Bundle.main.resourceURL!.appendingPathComponent("web", isDirectory: true)
            var page = URLComponents(url: dir.appendingPathComponent("index.html"), resolvingAgainstBaseURL: false)!
            page.fragment = "nogl"
            webView.loadFileURL(page.url!, allowingReadAccessTo: dir)
        } else {
            webView.reload()
        }
    }
}

#if DEBUG
private final class AutopilotLog: NSObject, WKScriptMessageHandler {
    func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
        NSLog("AUTOPILOT %@", String(describing: m.body))
    }
}
#endif

/// WKUserContentController keeps its handlers alive; this breaks the retain cycle.
private final class WeakMessageHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    init(_ target: WKScriptMessageHandler) { self.target = target }
    func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
        target?.userContentController(c, didReceive: m)
    }
}
