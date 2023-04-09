import Spinner from 'react-bootstrap/Spinner';

function LoadingSpinner(props) {
  if (props.isLoading) {
    return (
      <Spinner animation='border' role='status' variant='primary'>
        <span className='visually-hidden'>Loading...</span>
      </Spinner>
    );
  }
}

export default LoadingSpinner