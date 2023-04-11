import Button from 'react-bootstrap/Button';
import ButtonGroup from 'react-bootstrap/ButtonGroup';
import Stack from 'react-bootstrap/Stack';

function Voting({ size, voted, setVote }) {
  console.log(size);
  const message = size === undefined | size === 0 ? 'Start searching...'
    : !voted ? 'Do you like the results?'
    : 'Thank you!';

  const disabled = voted || size === undefined || size === 0;

  return (
    <Stack direction='horizontal' gap={3}>
      {message}
        <ButtonGroup aria-label='Voting'>
          <Button disabled={disabled} variant='outline-success' onClick={() => setVote(true)}>👍</Button>
          <Button disabled={disabled} variant='outline-danger' onClick={() => setVote(false)}>👎</Button>
        </ButtonGroup>
    </Stack>
  );
}

export default Voting;