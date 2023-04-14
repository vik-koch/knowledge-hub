import Button from 'react-bootstrap/Button';
import ButtonGroup from 'react-bootstrap/ButtonGroup';
import Stack from 'react-bootstrap/Stack';

function Voting({ size, voted, setVote }) {

  const disabled = voted || size === undefined || size === 0 || isNaN(size);
  console.log(size);
  return (
    
    <Stack direction='horizontal' gap={3}>
        <Button disabled={disabled} variant='secondary' onClick={() => setVote('left')}>{'<<<'}</Button>
        <div>Which results are better?</div>
        <Button disabled={disabled} variant='secondary' onClick={() => setVote('right')}>{'>>>'}</Button>
    </Stack>
  );
}

export default Voting;