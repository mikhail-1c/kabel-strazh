import SwiftUI

struct ContentView: View {
    @AppStorage("armed") private var armed = false
    @AppStorage("stealth") private var stealth = true

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    statusCard
                    if armed {
                        checklist
                        Button("Выключить") { armed = false }
                            .buttonStyle(.bordered)
                    } else {
                        Button("Включить сейчас") { armed = true }
                            .buttonStyle(.borderedProminent)
                            .tint(Color(red: 0.91, green: 0.72, blue: 0.29))
                        Text(stealth
                             ? "Обычный день: ничего не сторожит. iPhone сам режет кабель, если включить защиты ниже."
                             : "Третье приложение на iPhone не умеет глушить USB. Эта сборка — чеклист системных защит на экстренный случай.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(20)
            }
            .background(Color(red: 0.04, green: 0.05, blue: 0.06))
            .navigationTitle(stealth ? "Заряд" : "Кабель-страж")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Toggle("Скрытый вид", isOn: $stealth)
                        .toggleStyle(.switch)
                        .labelsHidden()
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private var statusCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(armed ? "Включён" : "Выключен")
                .font(.title2.weight(.semibold))
            Text(armed
                 ? "Пройдите пункты. Пока они выключены в системе, кабель может отдать бэкап."
                 : "Служба спит. Кабель никто не сторожит.")
                .font(.subheadline)
        }
        .foregroundStyle(Color(red: 0.04, green: 0.05, blue: 0.06))
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(armed ? Color(red: 0.24, green: 0.86, blue: 0.59) : Color(red: 0.60, green: 0.64, blue: 0.70))
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private var checklist: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Сделайте в системе")
                .font(.headline)
            step("1", "Код-пароль и Face ID / Touch ID")
            step("2", "Настройки → Face ID и код-пароль → USB-аксессуары: не открывать без разблокировки")
            step("3", "Настройки → Конфиденциальность и безопасность → Режим блокировки")
            step("4", "Кража устройства: дополнительная задержка на смену Apple ID и кода")
            step("5", "Не нажимайте «Доверять этому компьютеру» на чужом кабеле")
            Button("Открыть настройки iPhone") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .buttonStyle(.bordered)
        }
        .foregroundStyle(Color(red: 0.96, green: 0.95, blue: 0.92))
    }

    private func step(_ number: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(number)
                .font(.caption.weight(.bold))
                .frame(width: 22, height: 22)
                .background(Color(red: 0.91, green: 0.72, blue: 0.29))
                .foregroundStyle(Color(red: 0.04, green: 0.05, blue: 0.06))
                .clipShape(Circle())
            Text(text)
                .font(.subheadline)
        }
    }
}

#Preview {
    ContentView()
}
