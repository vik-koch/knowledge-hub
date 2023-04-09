import Fade from 'react-bootstrap/Fade';
import Badge from 'react-bootstrap/Badge';

function ErrorMessage(props) {
  return (
    <Fade in={props?.error}>
      <h3 className='mb-0'><Badge bg='secondary'>Unable to send a request!</Badge></h3>
    </Fade>
  );
}

export default ErrorMessage