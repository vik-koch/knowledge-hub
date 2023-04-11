import Fade from 'react-bootstrap/Fade';
import Spinner from 'react-bootstrap/Spinner';

function LoadingSpinner(props) {
  return (
    <Fade in={props?.isLoading}>
      <Spinner animation='border' role='status' variant='primary'>
        <span className='visually-hidden'>Loading...</span>
      </Spinner>
    </Fade>
  );
}

export default LoadingSpinner