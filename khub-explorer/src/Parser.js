const zipTitleWithLinks = (titles, links) => titles.map((title, i) => ({title: title, link: links[i]})); 
const options = { year: 'numeric', month: 'long', day: 'numeric' };
const nodeNames = ['P', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6'];
const highlightRegex = /@@@(end)*hl@@@/g;

function parseSparqlElements(elements) {
  const result = [];

  if (elements.results.bindings != null) {
    elements.results.bindings.forEach(element => {
      result.push(parseSparqlElement(element));
    });
  }
  return result;

}

function parseSparqlElement(element) {
  const result = {};

  result.link = element.link.value;
  result.email = element.email?.value;

  // Title with Placeholder for MS Posts
  if (element.title) {
    result.title = element.title.value;
  } else if (element.types.value.includes('Post')){
    result.title = 'MS Teams Message'
  }

  // Creation Time
  if (element.creationTime) {
    const creationDate = new Date(element.creationTime.value);
    result.creationTime = creationDate.toLocaleDateString('en-UK', options);
  }

  // Last Update Time
  if (element.lastUpdateTime) {
    const lastUpdateTime = new Date(element.lastUpdateTime.value);
    result.lastUpdateTime = lastUpdateTime.toLocaleDateString('en-UK', options);
  }

  // Type
  result.type = element.types.value.includes('Confluence') ? 'Confluence' 
              : element.types.value.includes('Teams') ? 'Teams' 
              : '';

  // Content snippet
  let content = '';
  const document = new DOMParser().parseFromString(element.content?.value, 'text/html').body;

  if (result.type === 'Confluence') {
    const snippet = parseChildNodes(document.childNodes);
    content = snippet.slice(0, 7).join(' · ');
  } else {
    content = document.innerText;
  }

  const snippetSize = 300;
  result.content = content.length < snippetSize ? content 
                  : content.substring(0, snippetSize) + '...'

  // Ancestors
  const titles = element.ancestorTitles?.value.split('///');
  const links = element.ancestorLinks?.value.split('///');
  if (titles && links && titles?.length === links?.length) {
    const zipped = zipTitleWithLinks(titles, links);
    result.ancestors = zipped.reverse();
  }

  return result;
}

function parseChildNodes(childNodes) {
  let result = []
  childNodes.forEach(element => {
    const text = element.innerText;
    if (nodeNames.includes(element.nodeName) && text != null && /\S/.test(text)) {
      result.push(text.replace(/\u00a0/g, ' '));
    } else {
      result.push.apply(result, parseChildNodes(element.childNodes));
    }
  });
  return result;
}

function parseConfluenceElements(elements) {
  const result = [];

  if (elements.results != null) {
    elements.results.forEach(element => {
      if (element.content?.type === 'page') {
        result.push(parseConfluenceElement(element, elements?._links?.base));
      }
    });
  }

  return result;
}

function parseConfluenceElement(element, baseUrl) {
  const result = {};

  result.link = baseUrl + element.url;
  result.title = element.title.replaceAll(highlightRegex, '');
  
  const lastUpdateTime = new Date(element.lastModified);
  if (element.content?.ancestors?.length !== 0) {
    result.lastUpdateTime = lastUpdateTime.toLocaleDateString('en-UK', options);
    result.content = element.excerpt
      .replaceAll(highlightRegex, '')
      .replaceAll(/(\n)+/g, ' · ')
      .replaceAll(/\s\s+/g, ' ')
  }

  result.type = 'Confluence';
  
  let ancestors = []
  element.content?.ancestors.forEach(element => {
    ancestors.push({
      title: element.title.replaceAll(highlightRegex, ''),
      link: baseUrl + element._links.webui
    })
  });
  result.ancestors = ancestors.length !== 0 ? ancestors : null;

  return result;
}

export { parseSparqlElements, parseConfluenceElements } 