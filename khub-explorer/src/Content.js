import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Stack from 'react-bootstrap/Stack';

function Content(props) {
    if (props.content) {
      const result = props.content.map(element => {
        return <Row>
                <Col xs='auto'>
                {element.type === 'Confluence' && <img src='./confluence.svg' width='24' alt='Confluence' />}
                {element.type === 'Teams' && <img src='./teams.svg' width='24' alt='MS Teams' />}
                </Col>
                <Col>
                  <Stack direction='vertical' gap={3}>
                    <div><a href={element.link}>{element.title}</a></div>
                    <Stack direction='horizontal' >
                      <Stack direction='horizontal' gap={2}>
                        {element.ancestors && element.ancestors
                          .map(item => {return <a href={item.link}>{item.title}</a>})
                          .reduce((prev, curr) => [ prev, ' / ', curr ])}
                      </Stack>
                      {element.lastUpdateTime && <span>・{element.lastUpdateTime}</span>}
                    </Stack>
                    {element.content}
                  </Stack>
                </Col>
               </Row>
      })
  
      return <div>{result}</div>
    }
}

export {Content};