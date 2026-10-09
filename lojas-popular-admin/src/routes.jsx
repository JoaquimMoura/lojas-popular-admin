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

import GestaoLayout from "./pages/gestao/GestaoLayout";
import PedidosPage from "./pages/gestao/PedidosPage";
import PedidoDetalhePage from "./pages/gestao/PedidoDetalhePage";
import ComprovantePage from "./pages/gestao/ComprovantePage";
import NovaVendaPage from "./pages/gestao/NovaVendaPage";
import ClientesPage from "./pages/gestao/ClientesPage";
import ClienteDetalhePage from "./pages/gestao/ClienteDetalhePage";
import EstoquePage from "./pages/gestao/EstoquePage";
import ConfiguracaoComercialPage from "./pages/gestao/ConfiguracaoComercialPage";
import UsuariosPage from "./pages/gestao/UsuariosPage";
import AgendaPage from "./pages/gestao/AgendaPage";
import EncomendasPage from "./pages/gestao/EncomendasPage";
import PosVendaPage from "./pages/gestao/PosVendaPage";
import PosVendaDetalhePage from "./pages/gestao/PosVendaDetalhePage";
import FinanceiroLayout, { FinanceiroIndex } from "./pages/gestao/financeiro/FinanceiroLayout";
import CaixaPage from "./pages/gestao/financeiro/CaixaPage";
import ContasPage from "./pages/gestao/financeiro/ContasPage";
import CartaoPage from "./pages/gestao/financeiro/CartaoPage";
import ComissoesPage, { MinhasComissoesPage } from "./pages/gestao/financeiro/ComissoesPage";
import MetasPage, { MinhaMetaPage } from "./pages/gestao/financeiro/MetasPage";
import FechamentoPage from "./pages/gestao/financeiro/FechamentoPage";
import RestituicoesPage from "./pages/gestao/financeiro/RestituicoesPage";
import RelatoriosPage from "./pages/gestao/financeiro/RelatoriosPage";
import CustosPage from "./pages/gestao/financeiro/CustosPage";

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
        handle: { noFooter: true },
        children: [
          {
            element: <PrivateRoute roles={["VENDEDOR"]} />,
            children: [{ index: true, element: <VendorDashboard /> }],
          },
          {
            element: <PrivateRoute roles={["VENDEDOR", "GERENTE"]} />,
            children: [
              { path: "produtos", element: <ProductsPage /> },
              { path: "categorias", element: <CategoriesPage /> },
            ],
          },
        ],
      },

      // gestão de vendas (Etapa 1)
      {
        path: "gestao",
        element: <PrivateRoute roles={["ADMIN", "GERENTE", "VENDEDOR"]} />,
        handle: { noFooter: true },
        children: [
          // visualização de impressão do comprovante (sem o menu da gestão)
          { path: "pedidos/:id/imprimir", element: <ComprovantePage /> },
          {
            element: <GestaoLayout />,
            children: [
              { index: true, element: <PedidosPage /> },
              { path: "pedidos", element: <PedidosPage /> },
              { path: "pedidos/:id", element: <PedidoDetalhePage /> },
              { path: "vendas/nova", element: <NovaVendaPage /> },
              { path: "vendas/:id/editar", element: <NovaVendaPage /> },
              { path: "clientes", element: <ClientesPage /> },
              { path: "clientes/:id", element: <ClienteDetalhePage /> },
              { path: "estoque", element: <EstoquePage /> },
              { path: "agenda", element: <AgendaPage /> },
              {
                path: "encomendas",
                element: (
                  <PrivateRoute roles={["ADMIN", "GERENTE"]}>
                    <EncomendasPage />
                  </PrivateRoute>
                ),
              },
              {
                path: "pos-venda",
                element: (
                  <PrivateRoute roles={["ADMIN", "GERENTE"]}>
                    <PosVendaPage />
                  </PrivateRoute>
                ),
              },
              {
                path: "pos-venda/:id",
                element: (
                  <PrivateRoute roles={["ADMIN", "GERENTE"]}>
                    <PosVendaDetalhePage />
                  </PrivateRoute>
                ),
              },
              {
                path: "financeiro",
                element: (
                  <PrivateRoute roles={["ADMIN", "GERENTE"]}>
                    <FinanceiroLayout />
                  </PrivateRoute>
                ),
                children: [
                  { index: true, element: <FinanceiroIndex /> },
                  { path: "caixa", element: <CaixaPage /> },
                  { path: "contas", element: <ContasPage /> },
                  { path: "cartao", element: <CartaoPage /> },
                  { path: "comissoes", element: <ComissoesPage /> },
                  { path: "metas", element: <MetasPage /> },
                  { path: "fechamento", element: <FechamentoPage /> },
                  { path: "restituicoes", element: <RestituicoesPage /> },
                  { path: "relatorios", element: <RelatoriosPage /> },
                  { path: "custos", element: <CustosPage /> },
                ],
              },
              {
                path: "minhas-comissoes",
                element: (
                  <PrivateRoute roles={["ADMIN", "GERENTE", "VENDEDOR"]}>
                    <MinhasComissoesPage />
                  </PrivateRoute>
                ),
              },
              {
                path: "minha-meta",
                element: (
                  <PrivateRoute roles={["ADMIN", "GERENTE", "VENDEDOR"]}>
                    <MinhaMetaPage />
                  </PrivateRoute>
                ),
              },
              { path: "produtos", element: <ProductsPage /> },
              { path: "categorias", element: <CategoriesPage /> },
              {
                path: "config-comercial",
                element: (
                  <PrivateRoute roles={["ADMIN", "GERENTE"]}>
                    <ConfiguracaoComercialPage />
                  </PrivateRoute>
                ),
              },
              {
                path: "usuarios",
                element: (
                  <PrivateRoute roles={["ADMIN"]}>
                    <UsuariosPage />
                  </PrivateRoute>
                ),
              },
            ],
          },
        ],
      },
    ],
  },
]);
