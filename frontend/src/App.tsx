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

const Placeholder = ({ name }: { name: string }) => (
  <div className="card">
    <h3>{name}</h3>
    <p className="muted">Coming in the next increment.</p>
  </div>
)

export default function App() {
  return (
    <ToasterProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<AppLayout />}>
              <Route path="/" element={<Placeholder name="Dashboard" />} />
              <Route path="/employees" element={<EmployeeDirectoryPage />} />
              <Route path="/employees/new" element={<EmployeeFormPage />} />
              <Route path="/employees/:id" element={<EmployeeDetailPage />} />
              <Route path="/employees/:id/edit" element={<EmployeeFormPage />} />
              <Route path="/bulk-raises" element={<BulkRaisesPage />} />
              <Route path="/review-queue" element={<ReviewQueuePage />} />
              <Route path="/payroll" element={<PayrollPage />} />
              <Route path="/audit" element={<Placeholder name="Audit feed" />} />
              <Route path="/settings" element={<SettingsPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ToasterProvider>
  )
}
