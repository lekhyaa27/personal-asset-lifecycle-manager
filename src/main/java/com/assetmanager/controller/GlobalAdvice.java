package com.assetmanager.controller;

import com.assetmanager.exception.ResourceNotFoundException;
import com.assetmanager.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

/** Adds the user name + notification count to every page, and turns exceptions into friendly error pages. */
@ControllerAdvice
public class GlobalAdvice {
    private final NotificationService notifications;
    public GlobalAdvice(NotificationService notifications) { this.notifications = notifications; }

    @ModelAttribute
    public void common(HttpSession session, Model model) {
        Object id = session.getAttribute("userId");
        if (id != null) {
            model.addAttribute("userName", session.getAttribute("userName"));
            model.addAttribute("notifCount", notifications.build((Long) id).size());
        }
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound(ResourceNotFoundException ex) { return err(404, ex.getMessage()); }

    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView badInput(IllegalArgumentException ex) { return err(400, ex.getMessage()); }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView tooBig(MaxUploadSizeExceededException ex) { return err(400, "File is too large (maximum 5 MB)."); }

    private ModelAndView err(int status, String msg) {
        ModelAndView mv = new ModelAndView("error");
        mv.setStatus(HttpStatus.valueOf(status));
        mv.addObject("status", status);
        mv.addObject("message", msg);
        return mv;
    }
}
