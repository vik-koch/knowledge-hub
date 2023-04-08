import Alert from 'react-bootstrap/Alert';

function ErrorMessage(props) {
  if (props.error === true) {
    return (
      <Alert variant='warning'>
        <Alert.Heading>Unable to send a request!</Alert.Heading>
      </Alert>
    );
  }
}

export default ErrorMessage