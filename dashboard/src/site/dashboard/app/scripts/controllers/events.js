var dbModule = angular.module('workbench-events.controller', ['workbench-logs.services']);

dbModule.controller("EventsController", ['$scope', '$http', '$location', '$routeParams', 'CommandLogApi', 'Monitor', '$modal', '$q', 'MetricConfig', '$route', '$window', function ($scope, $http, $location, $routeParams, CommandLogApi, Monitor, $modal, $q, MetricConfig, $route, $window) {

    var sql = "select * from Event";

    $scope.countPage = 5;
    $scope.countPageOptions = [5, 10, 20];
    $scope.selectedWhen = new Array;
    $scope.selectedWhat = new Array;

    $scope.refresh = function () {
        $scope.metadata = CommandLogApi.refreshMetadata('monitor', function (data) {
            $scope.eventsWhen = CommandLogApi.listClassesForSuperclass('EventWhen');
            $scope.eventsWhat = CommandLogApi.listClassesForSuperclass('EventWhat');
            $scope.selectedEventWhen = undefined;
            $scope.selectedEventWhat = undefined;
        });
    }

    $scope.translate = function () {
        var prova = "c'iao";
        console.log(replace(/'/))
    }
    $scope.refresh();

    $scope.getEvents = function () {
        CommandLogApi.queryText({database: $routeParams.database, language: 'sql', text: sql, shallow: 'shallow' }, function (data) {
            if (data) {
                $scope.headers = CommandLogApi.getPropertyTableFromResults(data.result);
                $scope.resultTotal = data.result;
                $scope.results = data.result.slice(0, $scope.countPage);
                $scope.currentPage = 1;
                console.log(new Array(Math.ceil(data.result.length / $scope.countPage)));
                $scope.numberOfPage = new Array(Math.ceil(data.result.length / $scope.countPage));

                $scope.selectedWhen = new Array;
                $scope.selectedWhat = new Array;
                $scope.resultTotal.forEach(function (elem, idx, array) {

                    if (elem.when != undefined) {
                        $scope.selectedWhen[elem.name] = elem.when['@class'];
                    }
                    if (elem.what != undefined) {
                        $scope.selectedWhat[elem.name] = elem.what['@class'];
                    }
                })

            }
        });
    }
    $scope.getEvents();
    $scope.switchPage = function (index) {
        if (index != $scope.currentPage) {
            $scope.currentPage = index;
            $scope.results = $scope.resultTotal.slice(
                (index - 1) * $scope.countPage,
                index * $scope.countPage
            );
        }
    }

    $scope.previous = function () {
        if ($scope.currentPage > 1) {
            $scope.switchPage($scope.currentPage - 1);
        }
    }
    $scope.next = function () {

        if ($scope.currentPage < $scope.numberOfPage.length) {
            $scope.switchPage($scope.currentPage + 1);
        }
    }


    $scope.onWhenChange = function (event, eventWhen) {
        modalScope = $scope.$new(true);

        modalScope.eventParent = event;
        if (event['when'] == undefined || event['when']['@class'] != $scope.selectedWhen[event.name] && $scope.selectedWhen[event.name] != undefined) {
            event['when'] = {};
            event['when']['@class'] = $scope.selectedWhen[event.name].trim();
            event['when']['@type'] = 'd';
        }
        else {
            event['when']['@class'] = eventWhen['@class'];
        }
        modalScope.eventWhen = event['when'];
        modalScope.parentScope = $scope;

        var modalPromise = $modal({template: 'views/eventWhen/' + event['when']['@class'].toLowerCase().trim() + '.html', scope: modalScope});
        $q.when(modalPromise).then(function (modalEl) {
            modalEl.modal('show');
        });
    }
    $scope.onWhatChange = function (event, eventWhat) {
        modalScope = $scope.$new(true);

        modalScope.eventParent = event;
        if (event['what'] == undefined || $scope.selectedWhat[event.name] != null && (event['what']['@class'] != $scope.selectedWhat[event.name].trim() && $scope.selectedWhat[event.name] != undefined)) {
            event['what'] = {};
            event['what']['@class'] = $scope.selectedWhat[event.name].trim();
            event['what']['@type'] = 'd';
        }
        else {
            event['what']['@class'] = eventWhat['@class'];
        }
        modalScope.eventWhat = event['what'];
        modalScope.parentScope = $scope;
        var modalPromise = $modal({template: 'views/eventWhat/' + event['what']['@class'].toLowerCase().trim() + '.html', scope: modalScope});
        $q.when(modalPromise).then(function (modalEl) {
            modalEl.modal('show');
        });
    }
    $scope.deleteEvent = function (event) {
        Utilities.confirm($scope, $modal, $q, {

            title: 'Warning!',
            body: 'You are dropping event ' + event['name'] + '. Are you sure?',
            success: function () {
                var sql = 'DELETE FROM Event WHERE name = ' + "'" + event['name'] + "'";

                CommandLogApi.queryText({database: $routeParams.database, language: 'sql', text: sql, limit: $scope.limit}, function (data) {
                    var index = $scope.results.indexOf(event);
                    $scope.results.splice(index, 1);
                    $scope.results.splice();
                });

            }

        });

    }
    $scope.refreshPage = function () {
        $scope.refresh();
        $route.reload();
    }
    $scope.saveEvents = function () {
        var logs = new Array;
        var resultsApp = JSON.parse(JSON.stringify($scope.results));

        resultsApp.forEach(function (elem, idx, array) {

            MetricConfig.saveConfig(elem, function (data) {
                    var index = array.indexOf(elem);
                    logs.push(data);
                    array.splice(index, 1);
                    if (array.length == 0) {
                        modalScope = $scope.$new(true);
                        modalScope.logs = logs;
                        modalScope.parentScope = $scope;
                        var modalPromise = $modal({template: 'views/server/eventsnotify.html', scope: modalScope});
                        $q.when(modalPromise).then(function (modalEl) {
                            modalEl.modal('show');
                        });


                    }
                }
            );


        })
    };

    $scope.newEvent = function () {
        var object = {"name": "", '@rid': "#-1:-1", "@class": "Event"};
        $scope.results.push(object);
    }
}
])
;

dbModule.controller("LogWhenController", ['$scope', '$http', '$location', '$routeParams', 'CommandLogApi', 'Monitor', '$modal', '$q', function ($scope, $http, $location, $routeParams, CommandLogApi, Monitor, $modal, $q) {

    $scope.levels = ['CONFIG', 'DEBUG', 'ERROR', 'INFO', 'WARN'];
//    $scope.alertValues = ["Greater then", "Less then"];


//    $scope.checkAlertValue = function () {
//        if ($scope.eventWhen['alertValue'] == undefined) {
//            $scope.eventWhen['type'] = null;
//            return true;
//        }
//        return false;
//    }

    $scope.checkValidForm = function () {

        if ($scope.eventWhen['info'] == undefined && $scope.eventWhen['type'] == undefined) {
            return true;
        }
        if ($scope.eventWhen['type'] == null) {
            return true;
        }
        return false;
    }
}]);

dbModule.controller("EventsNotifyController", ['$scope', '$http', '$location', '$routeParams', 'CommandLogApi', 'Monitor', '$modal', '$q', function ($scope, $http, $location, $routeParams, CommandLogApi, Monitor, $modal, $q) {
    $scope.close = function () {
        $scope.parentScope.refreshPage();

        $scope.hide();
    }

}]);
dbModule.controller("MetricsWhenController", ['$scope', '$http', '$location', '$routeParams', 'CommandLogApi', 'Monitor', '$modal', '$q', 'Metric', function ($scope, $http, $location, $routeParams, CommandLogApi, Monitor, $modal, $q, Metric) {

    $scope.alertValues = ["Greater then", "Less then"];
    $scope.parameters = ["value", "entries", "min", "max", "average", "total" ];
    Metric.getMetricTypes(null, function (data) {
        $scope.metric = new Array;
        $scope.metrics = data.result;
        if ($scope.metrics.length > 0) {
            for (m in $scope.metrics) {
                $scope.metric.push($scope.metrics[m]['name']);
            }
        }


    });
    $scope.changeMetric = function () {


        for (m in $scope.metrics) {
            if ($scope.metrics[m]['name'] == $scope.eventWhen['name']) {
                if ($scope.metrics[m]['type'] == 'CHRONO') {
                    $scope.parameters = ["entries", "min", "max", "average", "total" ];
                }
                else {
                    $scope.parameters = ["value"];
                }
            }
        }

    }

}
]);

dbModule.controller("SchedulerWhenController", ['$scope', '$http', '$location', '$routeParams', 'CommandLogApi', 'Monitor', '$modal', '$q', function ($scope, $http, $location, $routeParams, CommandLogApi, Monitor, $modal, $q) {

    $scope.properties = CommandLogApi.listPropertiesForClass($scope.eventWhen['@class'].trim());
}]);
dbModule.controller("HttpWhatController", ['$scope', '$http', '$location', '$routeParams', 'CommandLogApi', 'Monitor', '$modal', '$q', function ($scope, $http, $location, $routeParams, CommandLogApi, Monitor, $modal, $q) {
    $scope.methods = ["GET", "POST"];


    $scope.checkMethod = function () {

        if ($scope.eventWhat['method'] == 'POST') {

            return false
        }
        else {
            $scope.eventWhat['body'] = undefined;
            return true
        }
    }

}]);
dbModule.controller("MailWhatController", ['$scope', '$http', '$location', '$routeParams', 'CommandLogApi', 'Monitor', '$modal', '$q', function ($scope, $http, $location, $routeParams, CommandLogApi, Monitor, $modal, $q) {

    $scope.properties = $scope.eventWhat;
}]);
dbModule.controller("FunctionWhatController", ['$scope', '$http', '$location', '$routeParams', 'CommandLogApi', 'Monitor', '$modal', '$q', function ($scope, $http, $location, $routeParams, CommandLogApi, Monitor, $modal, $q) {


    $scope.languages = ['SQL', 'Javascript'];
    $scope.addParam = function () {
        if ($scope.eventWhat['parameters'] == undefined) {
            $scope.eventWhat['parameters'] = new Array;
        }

        $scope.eventWhat['parameters'].push('');
    }
    $scope.removeParam = function (index) {
        if ($scope.eventWhat != undefined && $scope.eventWhat['parameters'] != undefined) {
            console.log($scope.eventWhat);
            $scope.eventWhat['parameters'].splice(index, 1);

        }
    }
}]);




