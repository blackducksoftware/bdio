var network;

var nodes = new vis.DataSet();
var edges = new vis.DataSet();
var gephiImported;

var detailsElement = document.getElementById('bdio-details');
var container = document.getElementById('bdio-graph');

loadJSON('data/graph.json' + window.location.search, redrawAll, function(err) {
  detailsElement.innerHTML = "Failed to load graph.";
});

var data = {
  nodes: nodes,
  edges: edges
};
var options = {
  nodes: {
    shape: 'dot',
    font: {
	  face: 'Tahoma'
    }
  },
  edges: {
    width: 0.15,
    arrows: {
    	to: {
    		scaleFactor: 0.5
    	}
    },
    smooth: {
	  type: 'continuous'
    }
  },
  interaction: {
    tooltipDelay: 200,
    hideEdgesOnDrag: true
  },
  physics: {
    stabilization: false,
    barnesHut: {
	  gravitationalConstant: -10000,
	  springConstant: 0.002,
	  springLength: 150
    }
  }
};

network = new vis.Network(container, data, options);
network.on('click', function (params) {
  if (params.nodes.length > 0) {
    var data = nodes.get(params.nodes[0]); // get the data from selected node
    detailsElement.innerHTML = JSON.stringify(data.attributes, undefined, 3); // show the data in the div
  } else if (params.edges.length > 0) {
    var data = edges.get(params.edges[0]);
    detailsElement.innerHTML = JSON.stringify(data.attributes, undefined, 3);
  } else {
    detailsElement.innerHTML = "Click a node or edge to view details";
  }
})

/**
 * https://raw.githubusercontent.com/almende/vis/v4.17.0/examples/network/exampleUtil.js
 */
function loadJSON(path, success, error) {
  var xhr = new XMLHttpRequest();
  xhr.onreadystatechange = function () {
    if (xhr.readyState === 4) {
      if (xhr.status === 200) {
        success(JSON.parse(xhr.responseText));
      } else {
        error(xhr);
      }
    }
  };
  xhr.open('GET', path, true);
  xhr.send();
}

/**
 * This function fills the DataSets. These DataSets will update the network.
 */
function redrawAll(gephiJSON) {
  if (gephiJSON.nodes === undefined) {
    gephiJSON = gephiImported;
  } else {
    gephiImported = gephiJSON;
  }

  nodes.clear();
  edges.clear();

  var parsed = vis.network.gephiParser.parseGephi(gephiJSON, {
    fixed: false,
    parseColor: false
  });

  // add the parsed data to the DataSets.
  nodes.add(parsed.nodes);
  edges.add(parsed.edges);

  detailsElement.innerHTML = "Click a node or edge to view details";
  network.fit(); // zoom to fit
}
