import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import LoginPage from './pages/LoginPage';
import ApplyPage from './pages/ApplyPage';
import ApplicationsPage from './pages/ApplicationsPage';
import ApplicationDetailPage from './pages/ApplicationDetailPage';
import TaskStatusPage from './pages/TaskStatusPage';
import AdminReviewsPage from './pages/AdminReviewsPage';
import WorkflowTracePage from './pages/WorkflowTracePage';
import AdminFeedbackPage from './pages/AdminFeedbackPage';
import ProductsPage from './pages/ProductsPage';
import { getToken } from './utils/request';

function PrivateRoute({ children }: { children: React.ReactElement }) {
  if (!getToken()) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <PrivateRoute>
              <MainLayout />
            </PrivateRoute>
          }
        >
          <Route index element={<Navigate to="/apply" replace />} />
          <Route path="apply" element={<ApplyPage />} />
          <Route path="applications" element={<ApplicationsPage />} />
          <Route path="applications/:id" element={<ApplicationDetailPage />} />
          <Route path="tasks/:taskId" element={<TaskStatusPage />} />
          <Route path="products" element={<ProductsPage />} />
          <Route path="admin/reviews" element={<AdminReviewsPage />} />
          <Route path="admin/applications/:id/trace" element={<WorkflowTracePage />} />
          <Route path="admin/feedback" element={<AdminFeedbackPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
