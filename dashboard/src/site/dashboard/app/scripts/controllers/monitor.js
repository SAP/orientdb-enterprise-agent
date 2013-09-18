'use strict';

angular.module('MonitorApp')
    .controller('DashboardController', function ($scope, $location, $timeout, $modal, $q,$odialog, Monitor, Server, Notification) {


        (function poll() {

            Monitor.getServers(function (data) {
                $scope.servers = data.result;

                $scope.healthData = new Array;
                $scope.servers.forEach(function (elem, idx, arr) {
                    $scope.healthData.push({ label: elem.name, value: 100});
                });
            })

        })();
        $scope.refresh = function () {
            Monitor.getServers(function (data) {
                $scope.servers = data.result;

                $scope.healthData = new Array;
                $scope.servers.forEach(function (elem, idx, arr) {
                    $scope.healthData.push({ label: elem.name, value: 100});
                });
            })
        }
        $scope.addServer = function () {

            var modalScope = $scope.$new(true)
            modalScope.refresh = $scope.refresh;
            var modalPromise = $modal({template: 'views/settings/newModal.html', persist: true, show: false, backdrop: 'static', scope: modalScope});

            $q.when(modalPromise).then(function (modalEl) {
                modalEl.modal('show');
            });
        }
        $scope.getStatusLabel = function (status) {
            var label = 'label ';
            label += status == 'ONLINE' ? 'label-success' : 'label-important';
            return label;
        }
        $scope.editServer = function (rid) {

            var modalScope = $scope.$new(true);
            modalScope.refresh = $scope.refresh;
            modalScope.serverID = rid;
            var modalPromise = $modal({template: 'views/settings/editModal.html', persist: true, show: false, backdrop: 'static', scope: modalScope});

            $q.when(modalPromise).then(function (modalEl) {
                modalEl.modal('show');
            });
        }
        $scope.deleteServer = function (server) {

            $odialog.confirm({
                title: 'Warning!',
                body: 'You are removing Server ' + server.name  + '. Are you sure?',
                success: function () {
                    Server.delete(server['@rid'], function (data) {
                        $scope.refresh();
                    });
                }
            });

        }
        Notification.latest(function (data) {
            $scope.notifications = data.result;
        });

        $scope.login = function () {
            Monitor.connect($scope.username, $scope.password, function (data) {
                $location.path("/home");
            }, function (data) {
                console.log("ciao");
            });
        }
    });