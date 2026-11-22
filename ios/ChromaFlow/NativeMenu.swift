import SwiftUI
import WebKit

// The iPhone menu and Settings, drawn natively with Apple's Liquid Glass (iOS 26 and later;
// older iOS gets the system material). The page underneath still owns every setting: this
// reads its state (ChromaNativeModel) and presses its controls (ChromaNativeAct), so the
// iPhone and Android apps behave exactly the same.

// MARK: - Model (mirrors window.ChromaNativeModel in index.html)

struct SegItem: Decodable, Equatable, Identifiable { let id: String; let label: String; let on: Bool }
struct StyleItem: Decodable, Equatable, Identifiable { let id: String; let name: String; let tag: String; let on: Bool }
struct SliderItem: Decodable, Equatable, Identifiable {
    let id: String; let label: String; let hint: String
    let min: Double; let max: Double; let step: Double; let value: Double; let out: String; let hidden: Bool
}
struct SwitchItem: Decodable, Equatable, Identifiable { let id: String; let label: String; let hint: String; let on: Bool; let hidden: Bool }
struct ResetItem: Decodable, Equatable { let label: String; let armed: Bool; let disabled: Bool? }

struct MenuModel: Decodable, Equatable {
    let playing: Bool; let lede: String
    let styles: [StyleItem]
    let source: [SegItem]; let colorNote: String; let custom: [String]; let customShown: Bool; let bg: String
    let playSum: String; let sym: [SegItem]; let shapes: [SegItem]; let shapeOpts: Bool
    let shapeColors: [SegItem]; let shapePick: Bool; let shapeHex: String
    let playSliders: [SliderItem]; let auto: SwitchItem; let tap: SwitchItem
    let tuneFor: String; let edited: Bool
    let look: [SliderItem]; let motion: [SliderItem]; let brush: [SliderItem]; let toggles: [SwitchItem]
    let reset: ResetItem
    let quality: [SegItem]; let updStatus: String; let updLabel: String; let version: String
    let resetAll: ResetItem; let playStyle: String
}

// MARK: - Bridge to the page

@MainActor
final class MenuBridge: ObservableObject {
    @Published var model: MenuModel?
    @Published var showSettings = false
    weak var web: WKWebView?

    /// Reads the page's state. On a slow first start the page may not be ready yet: try again shortly.
    func refresh(attempt: Int = 0) {
        web?.evaluateJavaScript("window.ChromaNativeModel ? window.ChromaNativeModel() : null") { [weak self] r, _ in
            guard let self else { return }
            if r is String { self.apply(r) }
            else if attempt < 40 {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { self.refresh(attempt: attempt + 1) }
            }
        }
    }

    /// kind: "click", "value" (slider or color) or "check".
    func act(_ kind: String, _ id: String, _ value: Any? = nil) {
        let js = "window.ChromaNativeAct(\(Self.json(kind)), \(Self.json(id)), \(Self.json(value ?? NSNull())))"
        web?.evaluateJavaScript(js) { [weak self] r, _ in self?.apply(r) }
        // Two-tap reset buttons calm down on their own after a few seconds.
        if kind == "click" && id.hasPrefix("reset") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.7) { [weak self] in self?.refresh() }
        }
    }

    private func apply(_ result: Any?) {
        guard let s = result as? String, let d = s.data(using: .utf8),
              let m = try? JSONDecoder().decode(MenuModel.self, from: d), m != model else { return }
        model = m
    }

    private static func json(_ v: Any) -> String {
        guard let d = try? JSONSerialization.data(withJSONObject: v, options: .fragmentsAllowed) else { return "null" }
        return String(decoding: d, as: UTF8.self)
    }
}

// MARK: - Liquid Glass helpers

extension View {
    /// Apple's Liquid Glass on iOS 26+, the system's frosted material before that.
    @ViewBuilder
    func liquidGlass(_ radius: CGFloat, tint: Color? = nil, interactive: Bool = false) -> some View {
        if #available(iOS 26.0, *) {
            self.glassEffect(Self.glass(tint: tint, interactive: interactive), in: .rect(cornerRadius: radius))
        } else {
            self.background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
        }
    }

    @ViewBuilder
    func liquidGlassCapsule(tint: Color? = nil, interactive: Bool = true) -> some View {
        if #available(iOS 26.0, *) {
            self.glassEffect(Self.glass(tint: tint, interactive: interactive), in: .capsule)
        } else {
            self.background(.ultraThinMaterial, in: Capsule())
        }
    }

    @available(iOS 26.0, *)
    private static func glass(tint: Color?, interactive: Bool) -> Glass {
        var g = Glass.regular
        if let tint { g = g.tint(tint) }
        if interactive { g = g.interactive() }
        return g
    }

    @ViewBuilder
    func glassButton(prominent: Bool = false) -> some View {
        if #available(iOS 26.0, *) {
            if prominent { self.buttonStyle(.glassProminent) } else { self.buttonStyle(.glass) }
        } else {
            if prominent { self.buttonStyle(.borderedProminent) } else { self.buttonStyle(.bordered) }
        }
    }
}

enum Brand {
    static func display(_ size: CGFloat) -> Font { .custom("Unbounded", size: size).weight(.heavy) }
    static let muted = Color.white.opacity(0.62)
    static let danger = Color(red: 1, green: 0.42, blue: 0.42)
    static let rainbow = LinearGradient(colors: [Color(hex: "#ff2a6d"), Color(hex: "#ffb000"), Color(hex: "#b6ff3d"),
                                                 Color(hex: "#05d9e8"), Color(hex: "#6a5cff")], startPoint: .leading, endPoint: .trailing)
}

extension Color {
    init(hex: String) {
        var v: UInt64 = 0
        Scanner(string: hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))).scanHexInt64(&v)
        self.init(red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255, blue: Double(v & 255) / 255)
    }

    var hex: String {
        let c = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        c.getRed(&r, green: &g, blue: &b, alpha: &a)
        let f = { (x: CGFloat) in Int((min(max(x, 0), 1) * 255).rounded()) }
        return String(format: "#%02x%02x%02x", f(r), f(g), f(b))
    }
}

// MARK: - Root

struct NativeMenuRoot: View {
    @ObservedObject var bridge: MenuBridge

    var body: some View {
        ZStack {
            if let m = bridge.model {
                MenuScreen(m: m, bridge: bridge)
                    .opacity(bridge.showSettings ? 0 : 1)
                if bridge.showSettings {
                    SettingsScreen(m: m, bridge: bridge)
                        .transition(.move(edge: .trailing).combined(with: .opacity))
                }
            }
        }
        .animation(.spring(response: 0.38, dampingFraction: 0.86), value: bridge.showSettings)
        .preferredColorScheme(.dark)
        .tint(.white)
    }
}

// MARK: - Shared pieces

private struct SectionTitle: View {
    let text: String
    var body: some View {
        Text(text.uppercased()).font(.system(size: 12, weight: .semibold)).kerning(1.6).foregroundStyle(Brand.muted)
            .padding(.leading, 4)
    }
}

private struct Panel<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 14) { content }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .liquidGlass(22)
    }
}

private struct SegControl: View {
    let items: [SegItem]
    let bridge: MenuBridge
    var body: some View {
        Picker("", selection: Binding(
            get: { items.first(where: { $0.on })?.id ?? "" },
            set: { id in if id != items.first(where: { $0.on })?.id { bridge.act("click", id) } })) {
            ForEach(items) { Text($0.label).tag($0.id) }
        }
        .pickerStyle(.segmented)
    }
}

private struct Field<Content: View>: View {
    let label: String
    var hint: String = ""
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.system(size: 15, weight: .medium))
                if !hint.isEmpty { Text(hint).font(.system(size: 13)).foregroundStyle(Brand.muted) }
            }
            content
        }
    }
}

private struct SliderRow: View {
    let item: SliderItem
    let bridge: MenuBridge
    @State private var value: Double = 0
    @State private var editing = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.label).font(.system(size: 15))
                    if !item.hint.isEmpty { Text(item.hint).font(.system(size: 12.5)).foregroundStyle(Brand.muted) }
                }
                Spacer()
                Text(item.out).font(.system(size: 14).monospacedDigit()).foregroundStyle(Brand.muted)
            }
            Slider(value: Binding(get: { value }, set: { v in
                value = v
                bridge.act("value", item.id, v)
            }), in: item.min...item.max, step: item.step, onEditingChanged: { editing = $0 })
        }
        .onAppear { value = item.value }
        .onChange(of: item.value) { v in if !editing { value = v } }
    }
}

private struct SwitchRow: View {
    let item: SwitchItem
    let bridge: MenuBridge
    var body: some View {
        Toggle(isOn: Binding(get: { item.on }, set: { bridge.act("check", item.id, $0) })) {
            VStack(alignment: .leading, spacing: 2) {
                Text(item.label).font(.system(size: 15))
                if !item.hint.isEmpty { Text(item.hint).font(.system(size: 13)).foregroundStyle(Brand.muted) }
            }
        }
        .tint(Color(hex: "#8ef6ff"))
    }
}

private struct ColorDot: View {
    let hex: String
    let label: String
    let onPick: (String) -> Void
    var body: some View {
        ColorPicker(label, selection: Binding(get: { Color(hex: hex) }, set: { onPick($0.hex) }), supportsOpacity: false)
            .labelsHidden()
            .scaleEffect(1.25)
            .frame(width: 44, height: 44)
            .accessibilityLabel(label)
    }
}

private struct ResetButton: View {
    let item: ResetItem
    let id: String
    let bridge: MenuBridge
    var body: some View {
        Button { bridge.act("click", id) } label: {
            Text(item.label).font(.system(size: 15, weight: .semibold)).frame(maxWidth: .infinity).padding(.vertical, 8)
                .foregroundStyle(item.armed ? Color.black : Brand.danger)
        }
        .glassButton(prominent: item.armed)
        .tint(item.armed ? Brand.danger : Brand.danger.opacity(0.35))
        .disabled(item.disabled == true)
    }
}

private struct Scrim: View {
    var strength: Double
    var body: some View {
        LinearGradient(stops: [.init(color: .black.opacity(0.10 * strength), location: 0),
                               .init(color: .black.opacity(0.45 * strength), location: 0.28),
                               .init(color: .black.opacity(0.72 * strength), location: 1)],
                       startPoint: .top, endPoint: .bottom)
            .ignoresSafeArea()
            .allowsHitTesting(false)
    }
}

// MARK: - Home menu

private struct MenuScreen: View {
    let m: MenuModel
    let bridge: MenuBridge
    private let columns = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        ZStack(alignment: .bottom) {
            Scrim(strength: 1)
            ScrollViewReader { proxy in
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 24) {
                    header
                    VStack(alignment: .leading, spacing: 10) {
                        SectionTitle(text: "Style")
                        LazyVGrid(columns: columns, spacing: 10) {
                            ForEach(m.styles) { s in StyleTile(s: s) { bridge.act("click", "style-\(s.id)") } }
                        }
                    }
                    colors
                    playOptions.id("play-options")
                    fineTune.id("fine-tune")
                    Color.clear.frame(height: 90)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
            }
            .onAppear {
                // Debug screenshots: -nativeScroll play-options | fine-tune
                let args = ProcessInfo.processInfo.arguments
                if let i = args.firstIndex(of: "-nativeScroll"), i + 1 < args.count {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { proxy.scrollTo(args[i + 1], anchor: .top) }
                }
            }
            }
            playButton
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                Text("Chroma\nFlow").font(Brand.display(52)).lineSpacing(-6).foregroundStyle(.white)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer()
                Button { bridge.showSettings = true } label: {
                    Image(systemName: "gearshape").font(.system(size: 19, weight: .medium)).frame(width: 46, height: 46)
                }
                .buttonStyle(.plain)
                .liquidGlassCapsule()
                .accessibilityLabel("Settings")
            }
            Text(m.lede).font(.system(size: 16)).foregroundStyle(.white.opacity(0.85))
        }
    }

    private var colors: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionTitle(text: "Colors")
            Panel {
                SegControl(items: m.source, bridge: bridge)
                Text(m.colorNote).font(.system(size: 13)).foregroundStyle(Brand.muted)
                if m.customShown {
                    HStack(spacing: 14) {
                        ForEach(0..<min(4, m.custom.count), id: \.self) { i in
                            ColorDot(hex: m.custom[i], label: "My color \(i + 1)") { bridge.act("value", "c\(i)", $0) }
                        }
                    }
                }
                HStack(spacing: 12) {
                    ColorDot(hex: m.bg, label: "Background color") { bridge.act("value", "cbg", $0) }
                    Text("Background color").font(.system(size: 15))
                }
            }
        }
    }

    private var playOptions: some View {
        Disclosure(title: "Play options", subtitle: m.playSum) {
            Field(label: "Symmetry", hint: "Every stroke is copied around the screen") { SegControl(items: m.sym, bridge: bridge) }
            Field(label: "Touch shapes", hint: "Play with shapes instead of fluid") { SegControl(items: m.shapes, bridge: bridge) }
            if m.shapeOpts {
                Field(label: "Shape colors") { SegControl(items: m.shapeColors, bridge: bridge) }
                if m.shapePick {
                    HStack(spacing: 12) {
                        ColorDot(hex: m.shapeHex, label: "Shape color") { bridge.act("value", "shape-hex", $0) }
                        Text("Shape color").font(.system(size: 15))
                    }
                }
            }
            ForEach(m.playSliders.filter { !$0.hidden }) { SliderRow(item: $0, bridge: bridge) }
            SwitchRow(item: m.auto, bridge: bridge)
            SwitchRow(item: m.tap, bridge: bridge)
        }
    }

    private var fineTune: some View {
        Disclosure(title: "Fine-tune \(m.tuneFor)", subtitle: "Look, motion and brush for this style", badge: m.edited ? "Edited" : nil) {
            Text("Each style remembers its own settings. Watch the fluid behind this menu to see your changes live.")
                .font(.system(size: 13)).foregroundStyle(Brand.muted)
            group("Look", m.look)
            ForEach(m.toggles.filter { !$0.hidden }) { SwitchRow(item: $0, bridge: bridge) }
            group("Motion", m.motion)
            group("Brush", m.brush)
            ResetButton(item: m.reset, id: "reset", bridge: bridge)
        }
    }

    @ViewBuilder
    private func group(_ title: String, _ items: [SliderItem]) -> some View {
        Text(title).font(.system(size: 17, weight: .semibold)).padding(.top, 4)
        ForEach(items.filter { !$0.hidden }) { SliderRow(item: $0, bridge: bridge) }
    }

    private var playButton: some View {
        Button { bridge.act("click", "play") } label: {
            HStack(spacing: 10) {
                Text("Play").font(.system(size: 18, weight: .bold))
                Text(m.playStyle).font(.system(size: 16, weight: .medium)).opacity(0.6)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 10)
        }
        .glassButton(prominent: true)
        .tint(.white.opacity(0.9))
        .foregroundStyle(.black)
        .controlSize(.large)
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

private struct StyleTile: View {
    let s: StyleItem
    let tap: () -> Void
    var body: some View {
        Button(action: tap) {
            HStack(spacing: 12) {
                Image("swatch-\(s.id)").resizable().frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(s.name).font(.system(size: 15, weight: .semibold))
                    Text(s.tag).font(.system(size: 12.5)).foregroundStyle(Brand.muted).lineLimit(2)
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .frame(maxWidth: .infinity, minHeight: 70, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .liquidGlass(20, tint: s.on ? .white.opacity(0.18) : nil, interactive: true)
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous)
            .strokeBorder(.white.opacity(s.on ? 0.75 : 0), lineWidth: 1.5))
        .accessibilityAddTraits(s.on ? .isSelected : [])
    }
}

private struct Disclosure<Content: View>: View {
    let title: String
    var subtitle: String = ""
    var badge: String? = nil
    @ViewBuilder var content: Content
    @State private var open = ProcessInfo.processInfo.arguments.contains("-nativeOpen") // debug screenshots

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Button { withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { open.toggle() } } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 8) {
                            Text(title).font(.system(size: 17, weight: .semibold))
                            if let badge {
                                Text(badge.uppercased()).font(.system(size: 10.5, weight: .bold)).kerning(0.8)
                                    .padding(.horizontal, 8).padding(.vertical, 4)
                                    .foregroundStyle(Color(hex: "#8ef6ff")).background(Color(hex: "#8ef6ff").opacity(0.15), in: Capsule())
                            }
                        }
                        if !subtitle.isEmpty { Text(subtitle).font(.system(size: 13)).foregroundStyle(Brand.muted) }
                    }
                    Spacer()
                    Image(systemName: "plus").rotationEffect(.degrees(open ? 45 : 0)).foregroundStyle(Brand.muted)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if open { content }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .liquidGlass(22)
    }
}

// MARK: - Settings

private struct SettingsScreen: View {
    let m: MenuModel
    let bridge: MenuBridge

    var body: some View {
        ZStack {
            Scrim(strength: 1.25)
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 24) {
                    Button { bridge.showSettings = false } label: {
                        Label("Back", systemImage: "chevron.left").font(.system(size: 15, weight: .medium))
                            .padding(.horizontal, 6).padding(.vertical, 2)
                    }
                    .glassButton()
                    Text("Settings").font(Brand.display(46)).foregroundStyle(.white)

                    VStack(alignment: .leading, spacing: 10) {
                        SectionTitle(text: "Quality")
                        Panel {
                            SegControl(items: m.quality, bridge: bridge)
                            Text("Higher looks sharper but uses more battery. Pick a lower one if the fluid stutters.")
                                .font(.system(size: 13)).foregroundStyle(Brand.muted)
                        }
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        SectionTitle(text: "Updates")
                        Panel {
                            Text(m.updStatus).font(.system(size: 14)).foregroundStyle(Brand.muted)
                            Button { bridge.act("click", "upd-now") } label: {
                                Text(m.updLabel).font(.system(size: 15, weight: .semibold)).frame(maxWidth: .infinity).padding(.vertical, 8)
                            }
                            .glassButton(prominent: true)
                            .tint(.white.opacity(0.9))
                            .foregroundStyle(.black)
                            Text(m.version).font(.system(size: 13)).foregroundStyle(Brand.muted).frame(maxWidth: .infinity)
                        }
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        ResetButton(item: m.resetAll, id: "reset-all", bridge: bridge)
                        Text("Puts every style, slider, color and switch back to how the app came.")
                            .font(.system(size: 13)).foregroundStyle(Brand.muted).padding(.leading, 4)
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        SectionTitle(text: "About")
                        Panel {
                            HStack(spacing: 0) {
                                Text("Credits to Kothi (").foregroundStyle(Brand.rainbow)
                                Text("🐒")
                                Text(")").foregroundStyle(Brand.rainbow)
                            }
                            .font(.system(size: 16, weight: .bold))
                            LinkRow(title: "Website", detail: "chromaflow.vercel.app", url: "https://chromaflow.vercel.app")
                            LinkRow(title: "Privacy policy", url: "https://neerajreddyapi.github.io/ChromaFlow/privacy-policy.html")
                            LinkRow(title: "Source code", detail: "GitHub", url: "https://github.com/Neerajreddyapi/ChromaFlow")
                        }
                    }
                    Text("Crafted by Neeraj Reddy").font(.system(size: 13)).foregroundStyle(Brand.muted)
                        .frame(maxWidth: .infinity).padding(.bottom, 24)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
            }
        }
    }
}

private struct LinkRow: View {
    let title: String
    var detail: String = ""
    let url: String
    var body: some View {
        Button { if let u = URL(string: url) { UIApplication.shared.open(u) } } label: {
            HStack {
                Text(title).font(.system(size: 15))
                Spacer()
                if !detail.isEmpty { Text(detail).font(.system(size: 13)).foregroundStyle(Brand.muted) }
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Brand.muted)
            }
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
