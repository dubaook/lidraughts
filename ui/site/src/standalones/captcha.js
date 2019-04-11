$(function() {
  $('div.captcha').each(function() {
    var $captcha = $(this);
    var $board = $captcha.find('.mini-board');
    var $input = $captcha.find('input').val('');
    var cg = $board.data('draughtsground');
    var dests = JSON.parse(lidraughts.readServerFen($board.data('x')));
    for (var k in dests) dests[k] = dests[k].match(/.{2}/g);
    cg.set({
      turnColor: cg.state.orientation,
      captureLength: 1,
      movable: {
        free: false,
        dests: dests,
        color: cg.state.orientation,
        events: {
          after: function(orig, dest) {
            $captcha.removeClass('success failure');
            submit(orig + ' ' + dest);
          }
        }
      }
    });

    var submit = function(solution) {
      $input.val(solution);
      $.ajax({
        url: $captcha.data('check-url'),
        data: {
          solution: solution
        },
        success: function(data) {
          $captcha.toggleClass('success', data == 1);
          $captcha.toggleClass('failure', data != 1);
          if (data == 1) $board.data('draughtsground').stop();
          else setTimeout(function() {
            lidraughts.parseFen($board);
            $board.data('draughtsground').set({
              turnColor: cg.state.orientation,
              movable: {
                dests: dests
              }
            });
          }, 300);
        }
      });
    };
  });
});
