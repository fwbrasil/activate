# MASS STATEMENT #
Activate supports mass update/delete statements. Use then when you have to perform a really big update/delete operation. It’s recommended to be used only on migrations.


## MASS UPDATE ##
Example:

``` scala
update {
    (entity: MyEntity) => where(entity.attribute :== "string1") set (entity.attribute := "string2")
}
```
Multiple attributes update:

``` scala
update {
    (entity: MyEntity) => where(entity.attribute :== "string1") set (entity.attribute := "string2", entity.otherAttribute := 100)
}
```
## MASS DELETE ##
``` scala
delete {
    (entity: MyEntity) => where(entity.attribute :== "string1")
}
```