const zipTitleWithLinks = (titles, links) => titles.map((title, i) => ({title: title, link: links[i]})); 
const options = { year: 'numeric', month: 'long', day: 'numeric' };

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
  
    // Content snippet
    result.content = '';
    const childNodes = new DOMParser()
      .parseFromString(element.content?.value, "text/html")
      .body.childNodes;
    
    let snippet = []
    Array.from(childNodes).every(element => {
      if (element.textContent) snippet.push(element.innerText);
      if (snippet.length === 3) return false;
      return true;
    });
  
    const snippetSize = 200;
    const content = snippet.join(' ');
    result.content = content.length < snippetSize ? content 
                   : content.substring(0, snippetSize) + '...' 
  
    // Ancestors
    const titles = element.ancestorTitles?.value.split('///');
    const links = element.ancestorLinks?.value.split('///');
    if (titles && links && titles?.length === links?.length) {
      const zipped = zipTitleWithLinks(titles, links);
      result.ancestors = zipped.reverse();
    }
  
    // Type
    result.type = element.types.value.includes('Confluence') ? 'Confluence' 
                : element.types.value.includes('Teams') ? 'Teams' 
                : '';
    
    return result;
  
}

export {parseSparqlElements}