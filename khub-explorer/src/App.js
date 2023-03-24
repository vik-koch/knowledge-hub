import React, { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Container from 'react-bootstrap/Container';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Stack from 'react-bootstrap/Stack';

import 'bootstrap/dist/css/bootstrap.min.css';

import { Content } from './Content';
import { parseSparqlElements } from './Parser';

const fusekiEndpoint = "http://localhost:3030/dataset/";

const queryTemplate =
`
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX khub: <http://semanticweb.org/ontologies/khub#>
PREFIX text: <http://jena.apache.org/text#>
SELECT ?link ?title ?content ?email ?creationTime ?lastUpdateTime
       (GROUP_CONCAT(DISTINCT STRAFTER(STR(?type), "#"); SEPARATOR=", ") AS ?types)
       (GROUP_CONCAT(DISTINCT ?ancestor_title; SEPARATOR="///") AS ?ancestor_titles)
       (GROUP_CONCAT(DISTINCT ?ancestor_link; SEPARATOR="///") AS ?ancestor_links)

WHERE { (?result ?score) text:query (khub:search '$QUERY') .
        ?result khub:link ?link .
        ?result rdf:type ?type .
        OPTIONAL { ?result khub:title ?title } .
        OPTIONAL { ?result khub:content ?content } .
        OPTIONAL { ?result khub:email ?email } .
        OPTIONAL { ?result khub:creationTime ?creationTime } .
        OPTIONAL { ?result khub:lastUpdateTime ?lastUpdateTime } .
        OPTIONAL {
          ?result khub:ancestor+ ?ancestor .
          ?ancestor khub:title ?ancestor_title ;
                    khub:link ?ancestor_link .
        }
}
GROUP BY ?link ?title ?content ?email ?creationTime ?lastUpdateTime
LIMIT 10
`;

function App() {

  // Showable content
  const [content, setContent] = useState(null);
  const [error, setError] = useState(false);
  const [startTime, setStartTime] = useState(null);
  const [endTime, setEndTime] = useState(null);

  // Handle search button
  const handleClick = async (event) => {

    if (event.target[0].value === "") {
      setContent(null);

    } else {
      event.preventDefault();

      let query = event.target[0].value;
      query = queryTemplate.replaceAll('$QUERY', query);

      setStartTime(new Date());
      await fetch(fusekiEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/sparql-query',
        },
        body: query
      }).then(async (response) => {
        if (response.ok) {
          const result = await response.json();
          setEndTime(new Date());
          setContent(parseSparqlElements(result));
        }
      }).catch(async (rejected) => {
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
          <Container className='py-3' fluid='xxl'>
          <Stack direction='horizontal' gap={3}>
            <div><span role='img' aria-label='books'>📚</span> KHub Explorer</div>
            <Form className="d-flex" onSubmit={handleClick}>
              <Form.Control type="search" placeholder="Type keywords..." className="me-3" aria-label="Search" />
              <Button variant="primary" type='submit' >Search</Button>
            </Form>
          </Stack>
          </Container>
        </Col>
      </Row>
      <Row>
        <Col>
          <Container fluid='xxl'>
            <ErrorMessage error={error} />
            <Statistics size={content?.length} startTime={startTime} endTime={endTime}/>
            <Content content={content} />
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
  if (props.size && props.startTime && props.endTime) {
    return (
      <div>Retrieved {props.size} items in {((props.endTime - props.startTime) / 1000).toFixed(2)} seconds</div>
    )
  }
}

function delay(delay) {
  return new Promise( res => setTimeout(res, delay) );
}

export default App;