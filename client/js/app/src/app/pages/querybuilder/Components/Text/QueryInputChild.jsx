import React, { useContext } from 'react';
import AddPropertyButton from '../Buttons/AddPropertyButton';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import QueryDropdownForm from './QueryDropDownForm';
import SimpleForm from './SimpleForm';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import SimpleButton from '../Buttons/SimpleButton';

export default function QueryInputChild({ id }) {
  const { inputs, setInputs, childMap } = useContext(QueryInputContext);

  let index = inputs.findIndex((element) => element.id === id);
  let childArray = inputs[index].children;
  let currentType = inputs[index].type;

  const updateInput = (e) => {
    e.preventDefault();
    let newInputs = inputs.slice();
    let iterId = e.target.id.replace('v', '');
    let currentId = iterId.substring(0, 1);
    let index = newInputs.findIndex((element) => element.id === currentId);
    let children = newInputs[index].children;
    for (let i = 3; i < iterId.length; i += 2) {
      currentId = iterId.substring(0, i);
      index = children.findIndex((element) => element.id === currentId);
      children = children[index].children;
    }
    index = children.findIndex((element) => element.id === iterId);
    children[index].input = e.target.value;
    setInputs(newInputs);
  };

  /**
   * Returns a placeholder text for a SimpleForm component
   * @param {the id of the SimpleForm component} id
   * @returns Placeholder text
   */
  const setPlaceHolder = (id) => {
    let currentId = id.substring(0, 1);
    let index = inputs.findIndex((element) => element.id === currentId);
    let currentType = inputs[index].type;
    let children = inputs[index].children;
    if (id.length > 3) {
      for (let i = 3; i < id.length; i += 2) {
        currentId = id.substring(0, i);
        index = children.findIndex((element) => element.id === currentId);
        currentType = currentType + '_' + children[index].type;
        children = children[index].children;
      }
      const currentChoice = childMap[currentType];
      index = children.findIndex((element) => element.id === id);
      currentType = children[index].type;
      return currentChoice[currentType].type;
    } else {
      const currentChoice = childMap[currentType];
      index = children.findIndex((element) => element.id === id);
      currentType = children[index].type;
      return currentChoice[currentType].type;
    }
  };

  const removeRow = (id) => {
    let newInputs = inputs.slice();
    let currentId = id.substring(0, 1);
    let index = newInputs.findIndex((element) => element.id === currentId);
    let children = newInputs[index].children;
    for (let i = 3; i < id.length; i += 2) {
      currentId = id.substring(0, i);
      index = children.findIndex((element) => element.id === currentId);
      children = children[index].children;
    }
    index = children.findIndex((element) => element === id);
    children.splice(index, 1);
    setInputs(newInputs);
  };

  const inputList = childArray.map((child) => {
    return (
      <div key={child.id} id={child.id}>
        <QueryDropdownForm
          choices={childMap[currentType]}
          id={child.id}
          child={true}
        />
        {child.hasChildren ? (
          <>
            <AddPropertyButton id={child.id} />
          </>
        ) : (
          <SimpleForm
            id={`v${child.id}`}
            size="30"
            onChange={updateInput}
            placeholder={setPlaceHolder(child.id)}
          />
        )}
        <OverlayTrigger
          placement="right"
          delay={{ show: 250, hide: 400 }}
          overlay={<Tooltip id="button-tooltip">Remove row</Tooltip>}
        >
          <span>
            <SimpleButton
              id={`b${child.id}`}
              className="removeRow"
              onClick={() => removeRow(child.id)}
              children="-"
            ></SimpleButton>
          </span>
        </OverlayTrigger>
        <br />
        <Child
          type={currentType + '_' + child.type}
          child={child}
          onChange={updateInput}
          placeholder={setPlaceHolder}
          removeRow={removeRow}
        />
      </div>
    );
  });

  return <>{inputList}</>;
}

function Child({ child, type, onChange, placeholder, removeRow }) {
  const { childMap } = useContext(QueryInputContext);

  const nestedChildren = (child.children || []).map((child) => {
    return (
      <div key={child.id}>
        <QueryDropdownForm
          choices={childMap[type]}
          id={child.id}
          child={true}
        />
        {child.hasChildren ? (
          <>
            <AddPropertyButton id={child.id} />
          </>
        ) : (
          <SimpleForm
            id={`v${child.id}`}
            size="30"
            onChange={onChange}
            placeholder={placeholder(child.id)}
          />
        )}
        <OverlayTrigger
          placement="right"
          delay={{ show: 250, hide: 400 }}
          overlay={<Tooltip id="button-tooltip">Remove row</Tooltip>}
        >
          <span>
            <SimpleButton
              id={`b${child.id}`}
              className="removeRow"
              onClick={() => removeRow(child.id)}
              children="-"
            ></SimpleButton>
          </span>
        </OverlayTrigger>
        <br />
        <Child
          child={child}
          id={child.id}
          type={type + '_' + child.type}
          onChange={onChange}
          placeholder={placeholder}
          removeRow={removeRow}
        />
      </div>
    );
  });

  return <>{nestedChildren}</>;
}
