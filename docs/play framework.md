# PLAY FRAMEWORK #
The “**activate-play**” component has some classes to facilitate the Activate usage with the [Play Framework](http://www.playframework.com/) 2.2.

Take a look at the activate-example-play project. It is based on the computer-database example.

[https://github.com/fwbrasil/activate-example-play](https://github.com/fwbrasil/activate-example-play)

There also a non-blocking version:

[https://github.com/fwbrasil/activate-example-play-async](https://github.com/fwbrasil/activate-example-play-async)


## PLAY PLUGIN ##
To support play application reload, add this line to the **conf/play.plugins** file (create it if necessary):

``` scala
100:net.fwbrasil.activate.play.ActivatePlayPlugin
```
The plugin has full entity class loading/reloading support. It also supports migration loading/reloading, but not running already executed migrations.


## ENTITY FORM ##
Entity form is a Play Form specialized in Activate entities. Constructor example:

``` scala
val computerForm =
    EntityForm[Computer](
        _.name -> nonEmptyText,
        _.introduced -> optional(date("yyyy-MM-dd")),
        _.discontinued -> optional(date("yyyy-MM-dd")),
        _.company -> optional(entity[Company]))
```
Each attribute is referenced by his accessor in a type-safe way in the left. The right side determines the play form properties.

The entity form just hold attributes values. It doesn’t holds entity instances.


## FILLWITH ##
To fill a form with an entity instance attributes, use the “**fillWith**” method:

``` scala
computerForm.fillWith(aComputerInstance)
```

## BINDFROMREQUEST ##
To fill a form with the request values, use the “**bindFromRequest**” method:

``` scala
computerForm.bindFromRequest
```
## UPDATEENTITY ##
To update an entity instance with the form values use the “**updateEntity**” method:
``` scala
computerForm.bindFromRequest.fold(
    formWithErrors => BadRequest(html.editForm(id, formWithErrors)),
    computerData => {
        val computer = computerData.updateEntity(id)
        Home.flashing("success" -> "Computer %s has been updated".format(computer.name))
    })
```
## ASYNCUPDATEENTITY ##
Async variation example:
``` scala
computerForm.bindFromRequest.fold(
    formWithErrors =>
        Future.successful(BadRequest(html.editForm(id, formWithErrors, companyOptions))),
    computerData => {
        computerData.asyncUpdateEntity(id).map { computer =>
            Home.flashing("success" -> "Computer %s has been updated".format(computer.name))
        }
    })
```
## CREATEENTITY ##
To create an entity instance with the form values use the “**createEntity**” method:

``` scala
computerForm.bindFromRequest.fold(
    formWithErrors => BadRequest(html.createForm(formWithErrors)),
    computerData => {
        val computer = computerData.createEntity
        Home.flashing("success" -> "Computer %s has been created".format(computer.name))
    })
```
## ASYNCCREATEENTITY ##
Async variation example:
``` scala
computerForm.bindFromRequest.fold(
    formWithErrors =>
        Future.successful(BadRequest(html.createForm(formWithErrors, companyOptions))),
    computerData => {
        computerData.asyncCreateEntity.map { computer =>
            Home.flashing("success" -> "Computer %s has been created".format(computer.name))
        }
    })
```
