import UIKit
import WebKit

/// Hosts the fluid engine full screen. The page was written for Android and talks to a
/// `ChromaAndroid` object; here that object forwards to native code. The live wallpaper
/// is Android only (iOS has no live wallpapers), so the page hides that section.
final class FluidViewController: UIViewController, WKNavigationDelegate, WKScriptMessageHandler {

    private var web: WKWebView!
    private static let void = UIColor(red: 7 / 255, green: 8 / 255, blue: 12 / 255, alpha: 1)

    override func loadView() {
        let content = WKUserContentController()
        content.addUserScript(WKUserScript(source: """
            window.ChromaIOS = true;
            window.ChromaAndroid = {
              setPlaying: function (on) { window.webkit.messageHandlers.chroma.postMessage({ playing: !!on }); }
            };
            """, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        // Follow the phone's text size (Settings > Display & Brightness > Text Size), like Android.
        let textScale = Int((UIFontMetrics(forTextStyle: .body).scaledValue(for: 100)).rounded())
        content.addUserScript(WKUserScript(source: "document.documentElement.style.webkitTextSizeAdjust = '\(textScale)%';",
                                           injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        content.add(WeakMessageHandler(self), name: "chroma")

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
        view = web
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        let dir = Bundle.main.resourceURL!.appendingPathComponent("web", isDirectory: true)
        web.loadFileURL(dir.appendingPathComponent("index.html"), allowingReadAccessTo: dir)

        // Swiping in from the left edge works like Android's Back: it leaves play mode,
        // closes Settings or the setup screens. On the menu itself it does nothing.
        let edge = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(edgeSwipe(_:)))
        edge.edges = .left
        view.addGestureRecognizer(edge)
    }

    @objc private func edgeSwipe(_ g: UIScreenEdgePanGestureRecognizer) {
        guard g.state == .ended, g.translation(in: view).x > 60 else { return }
        web.evaluateJavaScript("window.chromaBack && window.chromaBack()", completionHandler: nil)
    }

    // Keep the screen awake while playing, like the Android app.
    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any], let playing = body["playing"] as? Bool else { return }
        UIApplication.shared.isIdleTimerDisabled = playing
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
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        UIApplication.shared.isIdleTimerDisabled = false
        webView.reload()
    }
}

/// WKUserContentController keeps its handlers alive; this breaks the retain cycle.
private final class WeakMessageHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    init(_ target: WKScriptMessageHandler) { self.target = target }
    func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
        target?.userContentController(c, didReceive: m)
    }
}
