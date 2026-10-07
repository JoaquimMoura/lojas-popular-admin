import { fmtMoney } from "../../utils/format";

export default function MoneyText({ value, className = "" }) {
  return <span className={className}>{fmtMoney(value)}</span>;
}
