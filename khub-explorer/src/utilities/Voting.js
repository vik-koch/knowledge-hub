import Button from 'react-bootstrap/Button';
import ButtonGroup from 'react-bootstrap/ButtonGroup';
import Stack from 'react-bootstrap/Stack';

function Voting({ voted, setVote }) {
  return (
    <Stack direction='horizontal' gap={3}>
      {!voted ? 'Do you like the results?' : 'Thank you!'}
        <ButtonGroup aria-label='Voting'>
          <Button disabled={voted} variant='outline-success' onClick={() => setVote(true)}>👍</Button>
          <Button disabled={voted} variant='outline-danger' onClick={() => setVote(false)}>👎</Button>
        </ButtonGroup>
    </Stack>
  );
}

export default Voting;