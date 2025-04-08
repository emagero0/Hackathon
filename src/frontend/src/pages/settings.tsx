import { SettingsHeader } from "../components/settings/settings-header.tsx"
import { NotificationSettings } from "../components/settings/notification-settings"
import { IntegrationSettings } from "../components/settings/integration-settings"
import { ThemeSettings } from "../components/settings/theme-settings"
import { UserSettings } from "../components/settings/user-settings"

export default function Settings() {
  return (
    <main className="flex min-h-screen flex-col p-6">
      <SettingsHeader />

      <div className="grid gap-6 md:grid-cols-2 mt-6">
        <div>
          <UserSettings />
        </div>
        <div>
          <NotificationSettings />
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2 mt-6">
        <div>
          <IntegrationSettings />
        </div>
        <div>
          <ThemeSettings />
        </div>
      </div>
    </main>
  )
}

