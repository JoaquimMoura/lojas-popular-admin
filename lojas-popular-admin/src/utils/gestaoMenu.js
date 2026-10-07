// src/utils/gestaoMenu.js — links da área /gestao conforme o perfil
export function gestaoLinks(user) {
  const roles = user?.roles ?? [];
  const admin = roles.includes("ADMIN");
  const gestor = admin || roles.includes("GERENTE");
  const links = [
    { to: "/gestao/pedidos", text: "Pedidos" },
    { to: "/gestao/vendas/nova", text: "Nova venda" },
    { to: "/gestao/clientes", text: "Clientes" },
    { to: "/gestao/agenda", text: "Agenda" },
    { to: "/gestao/estoque", text: "Estoque" },
    { to: "/gestao/produtos", text: "Produtos" },
    { to: "/gestao/categorias", text: "Categorias" },
  ];
  if (gestor) {
    links.push({ to: "/gestao/encomendas", text: "Encomendas" });
    links.push({ to: "/gestao/pos-venda", text: "Pós-venda" });
    links.push({ to: "/gestao/financeiro", text: "Financeiro" });
  } else if (roles.includes("VENDEDOR")) {
    links.push({ to: "/gestao/minhas-comissoes", text: "Minhas comissões" });
    links.push({ to: "/gestao/minha-meta", text: "Minha meta" });
  }
  if (gestor) links.push({ to: "/gestao/config-comercial", text: "Configuração comercial" });
  if (admin) links.push({ to: "/gestao/usuarios", text: "Usuários" });
  return links;
}
