# Stand-alone library of Bindy extract from Apache Camel


### Introduction

After reviewing available options for parsing fixed format files, I
found that Camel Bindy is the best one, but comes with a heavy
set of dependencies, if you wanted to use it outside of Camel.

So, here we are, completely repackaged library which removes CamelContext
and associated concepts.

### Limitations

The library only supports reading.

The writing (marshalling) code was removed. Currently I don't need to write fixed format files
(only read them), so I did not include the parts required to write them out.

Some of resource loading parts I did not understand and they seemed to pull in a lot
of dependencies. So I avoided including them.

