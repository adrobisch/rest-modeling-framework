<?php
/**
 * This file has been auto generated
 * Do not change it
 */

namespace Test\Types;

use Test\Types\JsonObjectModel;
use Test\Types\ResourceClassMap;

class PersonModel extends JsonObjectModel implements Person {
    /**
     * @var string
     */
    private $name;

    /**
     * @return string
     */
    public function getName()
    {
        if (is_null($this->name)) {
            $value = $this->raw(Person::FIELD_NAME);
            $this->name = (string)$value;
        }
        return $this->name;
    }

    /**
     * @param string $name
     * @return $this
     */
    public function setName($name)
    {
        $this->name = (string)$name;

        return $this;
    }

}
