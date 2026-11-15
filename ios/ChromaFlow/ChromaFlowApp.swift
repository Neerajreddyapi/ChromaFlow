import SwiftUI

/// Chroma Flow for iPhone and iPad: the same fluid engine and menu as the Android app
/// (app/src/main/assets/index.html, copied in at build time), full screen in a web view.
@main
struct ChromaFlowApp: App {
    var body: some Scene {
        WindowGroup {
            FluidView()
                .ignoresSafeArea()
                .statusBarHidden(true)
                .persistentSystemOverlays(.hidden)
                .preferredColorScheme(.dark)
        }
    }
}

struct FluidView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> FluidViewController { FluidViewController() }
    func updateUIViewController(_ controller: FluidViewController, context: Context) {}
}
