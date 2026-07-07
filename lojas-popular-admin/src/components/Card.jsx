export default function Card({ title, children }) {
  return (
    <div style={{
      background:'red',
      padding:20,
      borderRadius:10,
      marginBottom:20
    }}>
      <h2 style={{marginBottom:10}}>{title}</h2>
      {children}
    </div>
  );
}
