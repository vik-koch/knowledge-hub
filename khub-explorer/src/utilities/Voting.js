import Button from 'react-bootstrap/Button';
import Stack from 'react-bootstrap/Stack';

function Voting({ size, voted, setVote }) {
  const noResults = size === undefined || isNaN(size);

  if (size === 0) {
    return <div>Nothing found</div>
  }

  if (!noResults) {
    return (
      <Stack direction='horizontal' gap={3}>
          <Button disabled={voted} variant='secondary' onClick={() => setVote('left')}>{'<<<'}</Button>
          <div>Which results do you like more?</div>
          <Button disabled={voted} variant='secondary' onClick={() => setVote('right')}>{'>>>'}</Button>
      </Stack>
    );
  }

}

export default Voting;