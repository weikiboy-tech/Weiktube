package com.weiktube.app

internal object ReturnDislikeScript {
    val SCRIPT = """
        (function(){
          if(window.weiktubeDislikes)return;
          window.weiktubeDislikes=function(id,label){
            var existing=document.getElementById('weiktube-ryd-count');
            if(existing&&(existing.dataset.video!==id||!label))existing.remove();
            if(!label||new URL(location.href).searchParams.get('v')!==id)return false;
            var root=document.querySelector('ytm-slim-video-action-bar-renderer,.slim-video-action-bar-actions,ytm-segmented-like-dislike-button-renderer');
            var button=document.querySelector('.ytDislikeButtonViewModelHost button,#segmented-dislike-button button');
            if(!button&&root)button=Array.from(root.querySelectorAll('button,[role="button"]')).find(function(b){
              return /dislike|mag ich nicht|gefällt mir nicht/i.test(b.getAttribute('aria-label')||'');
            });
            if(!button)return false;
            var span=document.getElementById('weiktube-ryd-count');
            if(!span){span=document.createElement('span');span.id='weiktube-ryd-count';
              span.style.cssText='margin-inline-start:6px;font:inherit;color:inherit;pointer-events:none;white-space:nowrap';
              span.title='Return YouTube Dislike – teilweise geschätzte Daten';
            }
            span.dataset.video=id;
            if(span.textContent!==label)span.textContent=label;
            span.setAttribute('aria-label',label+' Dislikes, Schätzung von Return YouTube Dislike');
            if(span.parentElement!==button)button.appendChild(span);
            return true;
          };
        })();
    """.trimIndent()
}
