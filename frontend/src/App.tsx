import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ToasterProvider } from './components/Toaster'
import AppLayout from './layout/AppLayout'
import LoginPage from './pages/LoginPage'
import EmployeeDirectoryPage from './pages/EmployeeDirectoryPage'
import EmployeeFormPage from './pages/EmployeeFormPage'
import EmployeeDetailPage from './pages/EmployeeDetailPage'
import BulkRaisesPage from './pages/BulkRaisesPage'
import ReviewQueuePage from './pages/ReviewQueuePage'
import PayrollPage from './pages/PayrollPage'
import SettingsPage from './pages/SettingsPage'
import AuditFeedPage from './pages/AuditFeedPage'
import DashboardPage from './pages/DashboardPage'
import HomePage from './pages/HomePage'

export default function App() {
  return (
    <ToasterProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<AppLayout />}>
              <Route path="/" element={<HomePage />} />
              <Route path="/analytics" element={<DashboardPage />} />
              <Route path="/employees" element={<EmployeeDirectoryPage />} />
              <Route path="/employees/new" element={<EmployeeFormPage />} />
              <Route path="/employees/:id" element={<EmployeeDetailPage />} />
              <Route path="/employees/:id/edit" element={<EmployeeFormPage />} />
              <Route path="/bulk-raises" element={<BulkRaisesPage />} />
              <Route path="/review-queue" element={<ReviewQueuePage />} />
              <Route path="/payroll" element={<PayrollPage />} />
              <Route path="/audit" element={<AuditFeedPage />} />
              <Route path="/settings" element={<SettingsPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ToasterProvider>
  )
}
