<?php
/**
 * This file has been auto generated
 * Do not change it
 */

namespace Test\Types;

class CustomerModel extends JsonObject implements Customer {
    /**
     * @var Address
     */
    private $address;

    /**
     * @return Address
     */
    public function getAddress()
    {
        if (is_null($this->address)) {
            $value = $this->raw('address');
            $mappedClass = ResourceClassMap::getMappedClass(Address::class);
            if (is_null($value)) {
                return new $mappedClass([]);
            }
            $this->address = new $mappedClass($value);
        }
        return $this->address;
    }


}