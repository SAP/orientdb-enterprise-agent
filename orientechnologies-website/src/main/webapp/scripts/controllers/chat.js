'use strict';

/**
 * @ngdoc function
 * @name webappApp.controller:AboutCtrl
 * @description
 * # AboutCtrl
 * Controller of the webappApp
 */
angular.module('webappApp')
  .controller('ChatCtrl', function ($scope, Organization, $routeParams, $route, User, $timeout, BreadCrumb, $location, ChatService, $rootScope) {

    $scope.isNew = false;
    $scope.placeholder = "Click here to type a message. Enter to send.";
    $scope.clientId = $routeParams.id;

    $scope.sending = false;

    $scope.connected = ChatService.connected;

    $rootScope.$on('msg-received', function (e, msg) {

      if (msg.sender.name != $scope.currentUser.name) {
        if ($scope.clientId == msg.clientId) {
          $scope.$apply(function () {
            addNewMessage(msg);
            visit()

          });
        } else {
          $scope.$apply(function () {
            $scope.clients.forEach(function (c) {
              if (c.clientId == msg.clientId) {
                c.timestamp = new Date().getTime();
              }
            })
          });
        }
      }
    });


    $scope.loadMore = function () {
      Organization.all("clients").one($scope.clientId).all("room").customGET("", {
        before: $scope.messages[0].messages[0].id
      }).then(function (data) {
        var msg = aggregateMessage(data.reverse());
        msg.reverse();
        msg.forEach(function (e) {
          $scope.messages.unshift(e);
        })
      }).catch(function (e) {

      });
    }


    var findGroup = function (groups, date) {
      return groups.filter(function (g) {
        return g.date == date;
      })
    }
    var visit = function () {
      Organization.all("clients").one($scope.clientId).all("room").all('checkin').patch().then(function (data) {
        $scope.clients.forEach(function (c) {
          if (c.clientId == $scope.clientId) {
            c.lastVisit = new Date().getTime();
          }
        });
      }).catch(function (e) {

      })
    }
    var aggregateMessage = function (msg) {
      var newMsg = []
      var lastTime = null;
      var lastUser = null
      msg.forEach(function (m) {

        if (lastUser == m.sender.name) {
          var momentLast = moment(lastTime);
          var momentCurrent = moment(m.date);
          var diff = momentCurrent.diff(momentLast, "minutes");
          if (diff < 20) {
            var group = findGroup(newMsg, lastTime);
            group[0].messages.push(m);
          } else {
            lastUser = m.sender.name;
            lastTime = m.date;
            newMsg.push({
              sender: m.sender,
              date: lastTime,
              messages: [m]
            })
          }

        } else {
          lastUser = m.sender.name;
          lastTime = m.date;
          newMsg.push({
            sender: m.sender,
            date: lastTime,
            messages: [m]
          })
        }
      });
      return newMsg;
    }
    var addNewMessage = function (message) {


      var len = $scope.messages.length;
      if (len > 0) {
        var lastTime = $scope.messages[len - 1].date;
        var lastGroup = $scope.messages[len - 1];
        if (lastGroup.sender.name == message.sender.name) {

          var momentLast = moment(new Date(parseInt(lastTime)));
          var momentCurrent = moment(new Date(message.date));
          var diff = momentCurrent.diff(momentLast, "minutes");
          if (diff < 20) {
            lastGroup.messages.push(message);
          } else {
            $scope.messages.push({
              date: message.date,
              sender: message.sender,
              messages: [message]
            })
          }
        } else {
          $scope.messages.push({
            date: message.date,
            sender: message.sender,
            messages: [message]
          })
        }
      } else {
        $scope.messages.push({
          date: message.date,
          sender: message.sender,
          messages: [message]
        })
      }
    }
    User.whoami().then(function (data) {
      $scope.currentUser = data;
      $scope.$watch(function () {
        return ChatService.clients;
      }, function (clients) {
        if (clients.length > 0) {
          $scope.clients = clients;
          if (!$scope.clientId) {
            $scope.clientId = $scope.clients[0].clientId.toString();
            $scope.client = $scope.clients[0];
          } else {

            $scope.clients.forEach(function (c) {
              if (c.clientId == $scope.clientId) {
                $scope.client = c;
              }
            })
          }
          BreadCrumb.title = 'Room ' + $scope.client.name;
          getMessages();
        }
      })


      function getMessages() {
        if ($scope.clientId) {
          Organization.all("clients").one($scope.clientId).all("room").getList().then(function (data) {
            $scope.messages = aggregateMessage(data.plain().reverse())
          }).catch(function (e) {
            if (e.status == 400 && e.data) {
              $scope.isNew = true;
            }
          });
          Organization.all("clients").one($scope.clientId).all("room").all('actors').getList().then(function (data) {
            $scope.actors = data.plain();
          });
          visit()
        }
      }

    });


    $scope.createChat = function () {
      Organization.all("clients").one($scope.clientId).all("room").post().then(function (data) {
        var jacked = humane.create({baseCls: 'humane-jackedup', addnCls: 'humane-jackedup-success'})
        jacked.log("Room created");
        $route.reload();
      });
    }
    $scope.sendMessage = function () {
      $scope.sending = true;
      Organization.all("clients").one($scope.clientId).all("room").patch({body: $scope.current}).then(function (data) {
        $scope.current = null;
        addNewMessage(data);
        $scope.sending = false;
      }).catch(function () {
        $scope.sending = false;
      })
    }
  });