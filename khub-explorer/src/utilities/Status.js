function Status(props) {
  let status = ['🟡', 'Initializing'];
  if (props.isReachable != null) {
    status = props.isReachable === true
      ? ['🟢', 'Server available']
      : ['🔴', 'Server not reachable']
  }
  return <div title={status[1]}>{status[0]}</div>
}

export default Status