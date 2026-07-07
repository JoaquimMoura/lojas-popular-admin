// src/routes.jsx
import { createBrowserRouter, Outlet } from "react-router-dom";
import NavBar from "./components/NavBar";
import PrivateRoute from "./components/PrivateRoute";

import Fanpage from "./pages/Fanpage";
import LoginPage from "./pages/LoginPage";
import Dashboard from "./pages/Dashboard";
import EmailsPage from "./pages/EmailsPage";
import ProductsPage from "./pages/ProductsPage";
import CategoriesPage from "./pages/CategoriesPage";
import StoreConfigPage from "./pages/StoreConfigPage";
import VendorDashboard from "./pages/VendorDashboard";
import FanpageConfigPage from "./pages/FanpageConfigPage";

import StoreHome from "./pages/StoreHome";
import CategoryProducts from "./pages/CategoryProducts";
import ProductDetails from "./pages/ProductDetails";
import CartPage from "./pages/CartPage";

function RootLayout() {
  return (
    <>
      <NavBar />
      <div className="container py-3">
        <Outlet />
      </div>
    </>
  );
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <RootLayout />,
    children: [
      // públicas
      { index: true, element: <Fanpage /> },
      { path: "login", element: <LoginPage /> },
      { path: "loja", element: <StoreHome /> },
      { path: "categoria/:id", element: <CategoryProducts /> },
      { path: "produto/:id", element: <ProductDetails /> },
      { path: "carrinho", element: <CartPage /> },

      // admin protegida (usa Outlet interno)
      {
        path: "admin",
        element: <PrivateRoute roles={["ADMIN"]} />,
        children: [
          { index: true, element: <Dashboard /> },
          { path: "emails", element: <EmailsPage /> },
          { path: "produtos", element: <ProductsPage /> },
          { path: "categorias", element: <CategoriesPage /> },
          { path: "fanpage", element: <FanpageConfigPage /> },
          { path: "config", element: <StoreConfigPage /> },
        ],
      },
      {
        path: "vendedor",
        element: <PrivateRoute roles={["VENDEDOR"]} />,
        children: [
          { index: true, element: <VendorDashboard /> },
          { path: "produtos", element: <ProductsPage /> },
          { path: "categorias", element: <CategoriesPage /> },
        ],
      },
    ],
  },
]);
