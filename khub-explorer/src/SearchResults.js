import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Stack from 'react-bootstrap/Stack';
import Pagination from 'react-bootstrap/Pagination';

import { useState } from 'react';

function SearchResults(props) {

  const [currentPage, setCurrentPage] = useState(1);

  if (props.content) {
    const size = 10;

    let paginatedSearchResults = {};
    let pageKey = 1;
  
    let paginationItems = []
    for (let number = 1; number <= Math.ceil(props.content.length / size); number++) {
      paginationItems.push(
        <Pagination.Item 
          key={number} 
          tabIndex={number} 
          active={number === currentPage} 
          onClick={(event) => setCurrentPage(event.target.tabIndex)}>
            {number}
        </Pagination.Item>
      );
    }
  
    let lastPage = paginationItems.length;
  
    let searchResults = props.content.map((element, index) => <SearchResult key={index} {...element} />);
    for (let index = 0; index < searchResults.length; index += size) {
      paginatedSearchResults[pageKey] = searchResults.slice(index, index + size);
      pageKey++;
    }
  
    let leftLimit = currentPage - 1 > 5 ? 5 : currentPage - 1; 
  
    return (
      <div>
        {paginatedSearchResults[currentPage]}
        {lastPage > 1 &&
          <Pagination className='' >
            {currentPage !== 1 && <Pagination.Prev onClick={_ => setCurrentPage(currentPage - 1)}/>}
            {paginationItems.slice(currentPage - leftLimit - 1, currentPage + 5)}
            {currentPage !== lastPage && <Pagination.Next onClick={_ => setCurrentPage(currentPage + 1)}/>}
          </Pagination>
        }
      </div>
    );
  }
  
}

function SearchResult(element) {
  return (
    <Row className='my-4'>
      <Col xs='auto'>
        <SourceIcon type={element.type} />
      </Col>
      <Col>
        <Stack direction='vertical' gap={1}>
          <Header title={element.title} link={element.link}/>
          <Row>
            <Col className='text-muted col-sm-auto'>
              <Ancestors ancestors={element.ancestors} />
              <DateTimeInfo ancestors={element.ancestors} lastUpdateTime={element.lastUpdateTime} creationTime={element.creationTime} />
            </Col>
          </Row>
          <Body content={element.content}/>
        </Stack>
      </Col>
    </Row>
  );
}

function SourceIcon(element) {
  let imageInfo = element?.type === 'Confluence' ? ['./confluence.svg', 'Confluence']
    : element?.type === 'Teams' ? ['./teams.svg', 'MS Teams'] : null;

  return <img src={imageInfo && imageInfo[0]} width='24' alt={imageInfo[1]} />
}

function Header(element) {
  return (
    <div>
      <a href={element?.link} className="text-decoration-none">
        {element?.title}
      </a>
    </div>
  );
}

function Ancestors(element) {
  if (element.ancestors) {
    return element.ancestors
      .map((item, index) => {return (
        <small key={index} className='text-truncate'>
          <a href={item.link} className='text-reset text-decoration-none'>
            {item.title}
          </a>
        </small>
      )})
      .reduce((prev, curr) => [ prev, ' / ', curr ])
  }
}

function DateTimeInfo(element) {
  let separator = '';
  if (element.ancestors) separator = '・';
  return element.lastUpdateTime ? <small className='text-muted'>{separator}Updated {element.lastUpdateTime}</small>
       : element.creationTime ? <small className='text-muted'>{separator}Created {element.creationTime}</small>
       : null;
}

function Body(element) {
  return <div>{element.content}</div>
}

export { SearchResults };