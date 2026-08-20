import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import { AuthProvider } from './auth/AuthContext'
import AppLayout from './layout/AppLayout'
import LoginPage from './pages/LoginPage'
import EmployeeDirectoryPage from './pages/EmployeeDirectoryPage'
import { Typography } from 'antd'

const Placeholder = ({ name }: { name: string }) => (
  <Typography.Title level={4}>{name} — coming in the next increment</Typography.Title>
)

export default function App() {
  return (
    <ConfigProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<AppLayout />}>
              <Route path="/" element={<Placeholder name="Dashboard" />} />
              <Route path="/employees" element={<EmployeeDirectoryPage />} />
              <Route path="/employees/new" element={<Placeholder name="Create employee" />} />
              <Route path="/employees/:id" element={<Placeholder name="Employee detail" />} />
              <Route path="/bulk-raises" element={<Placeholder name="Bulk raises" />} />
              <Route path="/review-queue" element={<Placeholder name="Review queue" />} />
              <Route path="/payroll" element={<Placeholder name="Payroll" />} />
              <Route path="/audit" element={<Placeholder name="Audit feed" />} />
              <Route path="/settings" element={<Placeholder name="Settings" />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ConfigProvider>
  )
}
