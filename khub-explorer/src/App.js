import { useState, useEffect } from 'react';
import Alert from 'react-bootstrap/Alert';
import Container from 'react-bootstrap/Container';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Stack from 'react-bootstrap/Stack';

import 'bootstrap/dist/css/bootstrap.min.css';

import { SearchResults } from './SearchResults';
import { parseSparqlElements } from './Parser';

let fusekiEndpoint, fusekiService;
const pollingInterval = 3000;

if (typeof window !== 'undefined') {
  initializeEnvironment();
}

// Main application
function App() {

  const [queryTemplate, setQueryTemplate] = useState(null);
  const [reachabilityStatus, setReachabilityStatus] = useState(null);

  // Showable content
  const [content, setContent] = useState(null);
  const [error, setError] = useState(false);
  const [duration, setDuration] = useState(null);

  // Read query template from public
  useEffect(() => {
    const fetchQueryTemplate = async () => {
      const data = await (
        await fetch('/queryTemplate.sparql')
      ).text();
      setQueryTemplate(data);
    };
    
    fetchQueryTemplate();
  }, []);
  
  // Poll the fuseki endpoint
  useEffect(() => {
    const interval = setInterval(() => {
      fetch(fusekiEndpoint + '/$/ping')
        .then((response) => setReachabilityStatus(response.ok))
    }, pollingInterval);
    return () => clearInterval(interval);
  }, []);

  // Handle search button
  const handleClick = async (event) => {
    if (event.target[0].value === '') {
      setContent(null);
    } else {
      event.preventDefault();

      let query = queryTemplate.replace('$QUERY', event.target[0].value);
      let startTime = new Date();
      await fetch(fusekiEndpoint + '/' + fusekiService, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/sparql-query',
        },
        body: query
      }).then(async (response) => {
        if (response.ok) {
          const result = await response.json();
          setContent(parseSparqlElements(result));
          setDuration(((new Date() - startTime) / 1000).toFixed(2));
        }
      }).catch(async (rejected) => {
        console.log(rejected);
        setError(true);
        await delay(2000);
        setError(false);
      });

    }
  };

  return (
    <Container fluid>
      <Row className='bg-light'>
        <Col>        
          <Container className='py-3 d-flex justify-content-between' fluid='xxl'>
            <Col className='col-sm-auto'>
              <Stack direction='horizontal' gap={3}>
                <div><span role='img' aria-label='books'>📚</span> KHub Explorer</div>
                <Form className="d-flex" onSubmit={handleClick}>
                  <Form.Control type="search" placeholder="Type keywords..." className="me-3" aria-label="Search" />
                  <Button disabled={!reachabilityStatus} variant="primary" type='submit' >Search</Button>
                </Form>
              </Stack>
            </Col>
            <Col className='col-sm-auto align-self-center'>
              <Status endpoint={reachabilityStatus} />
            </Col>
          </Container>
        </Col>
      </Row>
      <Row>
        <Col>
          <Container fluid='xxl'>
            <ErrorMessage error={error} />
            <Statistics size={content?.length} duration={duration}/>
            <SearchResults content={content} />
          </Container>
        </Col>
      </Row>
    </Container>

  );
}

function ErrorMessage(props) {
  if (props.error === true) {
    return (
      <Alert variant='warning'>
        <Alert.Heading>Unable to send a request!</Alert.Heading>
      </Alert>
    );
  }
}

function Statistics(props) {
  if (props.size) {
    let text = props.size !== 0 
      ? `Retrived ${props?.size} items in ${props?.duration} seconds`
      : "No search results retrieved";

    return <div className='mt-3'>{text}</div>
  }
}

function Status(props) {
  let status = ['🟡', 'Initializing'];
  if (props.endpoint) {
    status = props.endpoint === true
      ? ['🟢', 'Server available']
      : ['🔴', 'Server not reachable']
  }
  return <div title={status[1]}>{status[0]}</div>
}

function delay(delay) {
  return new Promise( res => setTimeout(res, delay) );
}

function initializeEnvironment() {
  fusekiEndpoint = process.env.REACT_APP_FUSEKI_ENDPOINT;
  fusekiService = process.env.REACT_APP_FUSEKI_SERVICE;

  if (!fusekiEndpoint) {
    console.log('No environment variable for Fuseki endpoint found, the default value is used instead')
    fusekiEndpoint = 'http://localhost:3030';
  }

  if (!fusekiService) {
    console.log('No environment variable for Fuseki service found, the default value is used instead')
    fusekiService = 'dataset';
  }
}

export default App;