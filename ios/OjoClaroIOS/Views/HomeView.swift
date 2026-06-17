import SwiftUI

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 24) {
                Text("Ojo Claro AI")
                    .font(.system(size: 36, weight: .black))
                    .foregroundStyle(.white)
                    .accessibilityAddTraits(.isHeader)

                Text(viewModel.spokenText)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(22)
                    .background(Color.white.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                    .accessibilityLabel("Respuesta del asistente: \(viewModel.spokenText)")

                if viewModel.isLoading {
                    ProgressView()
                        .tint(.white)
                        .accessibilityLabel("Procesando solicitud")
                }

                Button {
                    Task { await viewModel.submit("describir qué tengo enfrente") }
                } label: {
                    Text("DESCRIBIR")
                        .font(.system(size: 28, weight: .black))
                        .frame(maxWidth: .infinity, minHeight: 88)
                }
                .buttonStyle(.borderedProminent)
                .tint(.white)
                .foregroundStyle(.black)
                .accessibilityLabel("Botón principal. Describir lo que tengo enfrente.")

                Button {
                    Task { await viewModel.submit("leer texto") }
                } label: {
                    Text("Leer texto")
                        .font(.system(size: 24, weight: .bold))
                        .frame(maxWidth: .infinity, minHeight: 72)
                }
                .buttonStyle(.bordered)
                .tint(.white)
                .accessibilityLabel("Leer texto con la cámara")

                Button {
                    Task { await viewModel.submit("necesito ayuda") }
                } label: {
                    Text("Pedir ayuda")
                        .font(.system(size: 24, weight: .bold))
                        .frame(maxWidth: .infinity, minHeight: 72)
                }
                .buttonStyle(.bordered)
                .tint(.red)
                .accessibilityLabel("Pedir ayuda o emergencia")
            }
            .padding(24)
        }
        .onAppear {
            viewModel.speakCurrentMessage()
        }
    }
}
