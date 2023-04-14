import { useState, useEffect } from 'react';

import Container from 'react-bootstrap/Container';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Stack from 'react-bootstrap/Stack';

import 'bootstrap/dist/css/bootstrap.min.css';

import SearchResults from './SearchResults';
import { parseSparqlElements, parseConfluenceElements } from './Parser';

import ErrorMessage from './utilities/ErrorMessage';
import LoadingSpinner from './utilities/LoadingSpinner';
import Statistics from './utilities/Statistics'
import Status from './utilities/Status'
import Voting from './utilities/Voting'

import axios from 'axios';

const pollingInterval = 3000;

// Main application
function App() {

  // Query template and configuration from public
  const [template, setTemplate] = useState(null);
  const [config, setConfig] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      const template = await (await fetch('/template.sparql')).text();
      const config = await (await fetch('/config.json')).json();
      setTemplate(template);
      setConfig(config);
    };
    fetchData();
  }, []);

  if (template != null && config != null) {
    return <Main template={template} config={config} />;
  } else {
    <div>Loading...</div>
  }
}

function Main(props) {

  // Helper states
  const [loading, setLoading] = useState(null);
  const [error, setError] = useState(false);

  // Search query
  const [query, setQuery] = useState(null);

  useEffect(() => {
    setQuery(JSON.parse(window.localStorage.getItem('query')));
  }, []);

  useEffect(() => {
    window.localStorage.setItem('query', JSON.stringify(query));
  }, [query]);

  // Random choice
  const [choice, setChoice] = useState(null);

  useEffect(() => {
    setChoice(JSON.parse(window.localStorage.getItem('choice')));
  }, []);

  useEffect(() => {
    window.localStorage.setItem('choice', choice);
  }, [choice]);

  // Voted flag
  const [voted, setVoted] = useState(null);

  useEffect(() => {
    setVoted(JSON.parse(window.localStorage.getItem('voted')));
  }, []);

  useEffect(() => {
    window.localStorage.setItem('voted', voted);
  }, [voted]);

  // Fuseki reachability status with local storage
  const [reachable, setReachable] = useState(null);
  useEffect(() => {
    setReachable(JSON.parse(window.localStorage.getItem('reachable')));
  }, []);

  useEffect(() => {
    window.localStorage.setItem('reachable', reachable);
  }, [reachable]);

  // Poll the fuseki endpoint
  useEffect(() => {
    const interval = setInterval(() => {
      fetch(props.config?.FUSEKI_ENDPOINT + '$/ping')
        .then((response) => setReachable(response.ok))
        .catch((_) => setReachable(false));
    }, pollingInterval);
    return () => clearInterval(interval);
  }, [props.config?.FUSEKI_ENDPOINT]);

  // Showable content with local storage
  const [content, setContent] = useState({results: null, duration: null});

  useEffect(() => {
    setContent(JSON.parse(window.localStorage.getItem('content')));
  }, []);

  useEffect(() => {
    window.localStorage.setItem('content', JSON.stringify(content));
  }, [content]);

  const onResolve = async (status, results, startTime, parse) => {
    setLoading(false);

    if (status === 200 || status === 'ok') {
      const parsedResults = parse(await results);
      const duration = ((new Date() - startTime) / 1000).toFixed(2);
      setContent({results: parsedResults, duration: duration});
      logData(null);
      setVoted(false);
    }
  }

  const onReject = async (rejected) => {
    setLoading(false);

    console.log(rejected);
    setContent({results: null, duration: null});

    setError(true);
    await new Promise(_ => setTimeout(_, 2000));
    setError(false);
  }

  const logData = (object) => {
    if (props.config?.LOGGING_ENDPOINT !== null) {
      let body = {
        timestamp: new Date(),
        uuid: props.config?.PERSONAL_UUID,
        query: query,
        source: choice === 0 ? 'fuseki' : 'confluence',
      };
      body = Object.assign(body, object);

      fetch(props.config?.LOGGING_ENDPOINT, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(body)
      })
      .then(response => console.log(response))
      .catch(rejected => console.log(rejected));
    }
  }

  const setVote = (value) => {
    logData({liked: value});
    setVoted(true);
  }

  // Handle search button
  const handleClick = async (event) => {
    if (event.target[0].value === '') {
      setContent({results: null, duration: null});
    } else {
      event.preventDefault();
      
      const query = event.target[0].value;
      setQuery(query);

      const random = Math.round(Math.random());
      setChoice(random);

      const startTime = new Date();
      setLoading(true);
      if (random === 0) {
        fetch(props.config?.FUSEKI_ENDPOINT + props.config?.FUSEKI_SERVICE, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/sparql-query',
          },
          body: props.template.replace('$QUERY', event.target[0].value)
        })
        .then(response => onResolve(response?.status, response?.json(), startTime, parseSparqlElements))
        .catch(rejected => onReject(rejected));
      } else {
        axios.get(`wiki/rest/api/search?cql=siteSearch~"${event.target[0].value}"&limit=50&expand=content.ancestors`, {
          headers: {
            Authorization: 'Basic ' + btoa(`${props.config?.CONFLUENCE_EMAIL}:${props.config?.CONFLUENCE_TOKEN}`),
          },
        })
        .then(response => onResolve(response?.status, response?.data, startTime, parseConfluenceElements))
        .catch(rejected => onReject(rejected));
      }
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
                <Form className='d-flex' onSubmit={handleClick}>
                  <Form.Control type='search' placeholder='Type keywords...' className='me-3' aria-label='Search' />
                  <Button disabled={!reachable} variant='primary' type='submit' >Search</Button>
                </Form>
                <LoadingSpinner isLoading={loading} />
                <ErrorMessage error={error} />
              </Stack>
            </Col>
            <Col className='col-sm-auto align-self-center'>
              <Stack direction='horizontal' gap={5}>
                <Voting size={content?.results?.length} voted={voted} setVote={setVote} />
                <Status isReachable={reachable} />
              </Stack>
            </Col>
          </Container>
        </Col>
      </Row>
      <Row>
        <Col>
          <Container fluid='xxl'>
            <Statistics size={content?.results?.length} duration={content?.duration}/>
            <SearchResults content={content?.results} />
          </Container>
        </Col>
      </Row>
    </Container>
  );
}

export default App;