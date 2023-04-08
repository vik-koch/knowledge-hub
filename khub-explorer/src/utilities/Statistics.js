function Statistics(props) {
  if (props.size != null) {
    let text = props.size !== 0 
      ? `Retrived ${props?.size} items in ${props?.duration} seconds`
      : "No search results retrieved";

    return <div className='mt-3'>{text}</div>
  }
}

export default Statistics