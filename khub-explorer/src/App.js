import { useState, useEffect } from 'react';

import Container from 'react-bootstrap/Container';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Stack from 'react-bootstrap/Stack';

import 'bootstrap/dist/css/bootstrap.min.css';

import SearchResults from './SearchResults';
import parseSparqlElements from './Parser';

import ErrorMessage from './utilities/ErrorMessage';
import LoadingSpinner from './utilities/LoadingSpinner';
import Statistics from './utilities/Statistics'
import Status from './utilities/Status'

let fusekiEndpoint, fusekiService;
const pollingInterval = 3000;

if (typeof window !== 'undefined') {
  initializeEnvironment();
}

// Main application
function App() {

  // Query template
  const [template, setTemplate] = useState(null);

  const [loading, setLoading] = useState(null);
  const [error, setError] = useState(false);

  // Fuseki reachability status
  const [reachable, setReachable] = useState(null);
  useEffect(() => {
    setReachable(JSON.parse(window.localStorage.getItem('reachable')));
  }, []);
  useEffect(() => {
    window.localStorage.setItem('reachable', reachable);
  }, [reachable]);

  // Showable content
  const [content, setContent] = useState(null);
  useEffect(() => {
    setContent(JSON.parse(window.localStorage.getItem('content')));
  }, []);
  useEffect(() => {
    window.localStorage.setItem('content', JSON.stringify(content));
  }, [content]);

  // Request duration for statistics
  const [duration, setDuration] = useState(null);
  useEffect(() => {
    setDuration(JSON.parse(window.localStorage.getItem('duration')));
  }, []);
  useEffect(() => {
    window.localStorage.setItem('duration', duration);
  }, [duration]);

  // Read query template from public
  useEffect(() => {
    const fetchTemplate = async () => {
      const data = await (
        await fetch('/template.sparql')
      ).text();
      setTemplate(data);
    };
    
    fetchTemplate();
  }, []);
  
  // Poll the fuseki endpoint
  useEffect(() => {
    const interval = setInterval(() => {
      fetch(fusekiEndpoint + '/$/ping')
        .then((response) => setReachable(response.ok))
        .catch((_) => setReachable(false));
    }, pollingInterval);
    return () => clearInterval(interval);
  }, []);

  // Handle search button
  const handleClick = async (event) => {
    if (event.target[0].value === '') {
      setContent(null);
    } else {
      event.preventDefault();

      let query = template.replace('$QUERY', event.target[0].value);
      let startTime = new Date();
      setLoading(true);

      await fetch(fusekiEndpoint + '/' + fusekiService, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/sparql-query',
        },
        body: query
      }).then(async (response) => {
        setLoading(false);

        if (response.ok) {
          const result = await response.json();
          const parsedResult = parseSparqlElements(result)
          setContent(parsedResult);
          setDuration(((new Date() - startTime) / 1000).toFixed(2));
        }
      }).catch(async (rejected) => {
        console.log(rejected);
        setLoading(false);
        setContent(null);

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
                <div>
                  {/* eslint-disable-next-line */}
                  <a href='#' className='text-reset text-decoration-none' onClick={(event) => setContent(null)}>
                    <span role='img' aria-label='books'>📚</span> KHub Explorer
                  </a>
                </div>
                <Form className="d-flex" onSubmit={handleClick}>
                  <Form.Control type="search" placeholder="Type keywords..." className="me-3" aria-label="Search" />
                  <Button disabled={!reachable} variant="primary" type='submit' >Search</Button>
                </Form>
                <LoadingSpinner isLoading={loading} />
                <ErrorMessage error={error} />
              </Stack>
            </Col>
            <Col className='col-sm-auto align-self-center'>
              <Status isReachable={reachable} />
            </Col>
          </Container>
        </Col>
      </Row>
      <Row>
        <Col>
          <Container fluid='xxl'>
            
            <Statistics size={content?.length} duration={duration}/>
            <SearchResults content={content} />
          </Container>
        </Col>
      </Row>
    </Container>

  );
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