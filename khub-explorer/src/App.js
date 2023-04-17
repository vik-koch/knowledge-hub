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
import Status from './utilities/Status'
import Voting from './utilities/Voting'

import axios from 'axios';

const pollingInterval = 3000;

// Main application
function App() {

  // Query template and configuration from public
  const [template, setTemplate] = useState(null);
  const [sizeTemplate, setSizeTemplate] = useState(null);
  const [config, setConfig] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      const template = await (await fetch('/template.sparql')).text();
      const sizeTemplate = await (await fetch('/sizeTemplate.sparql')).text();
      const config = await (await fetch('/config.json')).json();
      setTemplate(template);
      setSizeTemplate(sizeTemplate);
      setConfig(config);
    };
    fetchData();
  }, []);

  if (template != null && config != null) {
    return <Main template={template} sizeTemplate={sizeTemplate} config={config} />;
  } else {
    <div>Loading...</div>
  }
}

function Main(props) {

  // Helper states
  const [loading, setLoading] = useState(null);
  const [error, setError] = useState(false);

  // Random source
  const [random, setRandom] = useState(null);

  useEffect(() => {
    setRandom(JSON.parse(window.localStorage.getItem('random')));
  }, []);

  useEffect(() => {
    window.localStorage.setItem('random', JSON.stringify(random));
  }, [random]);

  // Logging info, incl. search query and source
  const [logging, setLogging] = useState(null);

  useEffect(() => {
    setLogging(JSON.parse(window.localStorage.getItem('logging')));
  }, []);

  useEffect(() => {
    window.localStorage.setItem('logging', JSON.stringify(logging));
  }, [logging]);

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
  const [content, setContent] = useState({left: null, right: null});

  useEffect(() => {
    setContent(JSON.parse(window.localStorage.getItem('content')));
  }, []);

  useEffect(() => {
    window.localStorage.setItem('content', JSON.stringify(content));
  }, [content]);

  const logData = (object) => {
    if (props.config?.LOGGING_ENDPOINT !== null) {
      let body = {
        timestamp: new Date(),
        uuid: props.config?.PERSONAL_UUID
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
    const sides = random === 0 ? ['fuseki', 'confluence'] : ['confluence', 'fuseki']
    const vote = value === 'left' ? sides[0] : sides[1];
    logData(Object.assign(logging, {vote: vote}));
    setVoted(true);
  }

  // Handle search button
  const handleClick = async (event) => {
    if (event.target[0].value === '') {
      setContent({left: null, right: null});
    } else {
      event.preventDefault();

      const query = event.target[0].value;
      setLoading(true);

      const queryFuseki = (template) => {
        return axios.post(props.config?.FUSEKI_ENDPOINT + props.config?.FUSEKI_SERVICE,
          template.replace('$QUERY', query), {
          headers: {
            'Content-Type': 'application/sparql-query',
          }
        });
      }

      const queryConfluence = () => {
        return axios.get(`wiki/rest/api/search?cql=siteSearch~"${query}"&limit=10&expand=content.ancestors`, {
          headers: {
            'Authorization': 'Basic ' + btoa(`${props.config?.CONFLUENCE_EMAIL}:${props.config?.CONFLUENCE_TOKEN}`),
          },
        });
      }

      Promise.all([queryFuseki(props.template), queryFuseki(props.sizeTemplate), queryConfluence()])
        .then(async (response) => {
        setLoading(false);
        const [fuseki, fusekiSize, confluence] = [response[0], response[1], response[2]];
        if (fuseki?.status === 200 && confluence?.status === 200) {
          const parsedFuseki = parseSparqlElements(fuseki.data);
          const parsedConfluence = parseConfluenceElements(confluence.data);

          const randomNumber = Math.round(Math.random());
          const [results1, results2] = randomNumber === 0 
            ? [parsedFuseki, parsedConfluence] 
            : [parsedConfluence, parsedFuseki]

          const logging = {
            query: query,
            fuseki: fusekiSize?.data?.results?.bindings[0]?.totalSize?.value,
            confluence: confluence?.data?.totalSize,
          }
          setLogging(logging);
          logData(logging);

          setContent({left: results1, right: results2});
          setVoted(false);
          setRandom(randomNumber);
        } else {
          console.log(response);

          setError(true);
          await new Promise(_ => setTimeout(_, 2000));
          setError(false);
        }
      });
    }
  };

  const size = content?.left?.length + content?.right?.length;

  return (
    <Container fluid>
      <Row className='bg-light'>
        <Col>
          <Container className='py-3 d-flex justify-content-between' fluid='xxl' style={{maxWidth: '1500px'}}>
            <Col className='col-sm-auto'>
              <Stack direction='horizontal' gap={3}>
                <div>
                  {/* eslint-disable-next-line */}
                  <a href='#' className='text-reset text-decoration-none' onClick={() => {setContent(null)}}>
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
                <Status isReachable={reachable} />
              </Stack>
            </Col>
          </Container>
        </Col>
      </Row>
      <Row>
        <Col>
          <Container fluid='xxl' style={{maxWidth: '1500px'}}>
            <Row>
              <Col className='d-flex justify-content-center pt-3'>
                <Voting size={size} voted={voted} setVote={setVote} />
              </Col>
            </Row>
            <Row>
              <Col>
                <SearchResults content={content?.left} />
              </Col>
              <div className='vr' style={{padding: '0', margin: '25px 15px 0 15px', visibility: size === undefined || isNaN(size) ? 'hidden' : 'visible'}}></div>
              <Col>
                <SearchResults content={content?.right} />
              </Col>
            </Row>
          </Container>
        </Col>
      </Row>
    </Container>
  );
}

export default App;