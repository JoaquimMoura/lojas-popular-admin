// src/routes.jsx
import { createBrowserRouter, Outlet, useMatches } from "react-router-dom";
import NavBar from "./components/NavBar";
import Footer from "./components/Footer";
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
  const matches = useMatches();
  const fullBleed = matches.some((m) => m.handle?.fullBleed);
  const noFooter = matches.some((m) => m.handle?.noFooter);

  return (
    <>
      <NavBar />
      {fullBleed ? (
        <Outlet />
      ) : (
        <div className="container py-3">
          <Outlet />
        </div>
      )}
      {!noFooter && <Footer />}
    </>
  );
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <RootLayout />,
    children: [
      // públicas
      { index: true, element: <Fanpage />, handle: { fullBleed: true } },
      { path: "login", element: <LoginPage /> },
      { path: "loja", element: <StoreHome /> },
      { path: "categoria/:id", element: <CategoryProducts /> },
      { path: "produto/:id", element: <ProductDetails /> },
      { path: "carrinho", element: <CartPage /> },

      // admin protegida (usa Outlet interno)
      {
        path: "admin",
        element: <PrivateRoute roles={["ADMIN"]} />,
        handle: { noFooter: true },
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
        handle: { noFooter: true },
        children: [
          { index: true, element: <VendorDashboard /> },
          { path: "produtos", element: <ProductsPage /> },
          { path: "categorias", element: <CategoriesPage /> },
        ],
      },
    ],
  },
]);
